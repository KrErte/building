#!/usr/bin/env python3
"""
BuildQuote IFC Extractor
Kasutus: python3 extract_ifc.py <input.ifc> <output.json>
Exit code 0 = OK, 1 = error (stderr sisaldab viga)

Uses IfcOpenShell to parse IFC files and extract building information
for construction quote generation.
"""
import ifcopenshell
import ifcopenshell.util.element as util_element
import ifcopenshell.util.unit as util_unit
import json
import sys
import time
import re
from collections import defaultdict


def extract(ifc_path: str) -> dict:
    """
    Ekstraktib kogu ehitusinfo IFC failist.
    Return: dict mis serialiseeritakse JSON-iks.
    """
    start = time.time()
    model = ifcopenshell.open(ifc_path)

    # Tuvasta ühikute konversioon (IFC võib kasutada mm, m, inch jne)
    # util_unit.calculate_unit_scale annab kordaja millega korrutada et saada meetrid
    try:
        length_scale = util_unit.calculate_unit_scale(model, "LENGTHUNIT")
    except Exception:
        length_scale = 1.0

    try:
        area_scale = util_unit.calculate_unit_scale(model, "AREAUNIT")
    except Exception:
        area_scale = 1.0

    try:
        volume_scale = util_unit.calculate_unit_scale(model, "VOLUMEUNIT")
    except Exception:
        volume_scale = 1.0

    result = {
        "fileInfo": extract_header(model),
        "project": extract_project(model),
        "spatialStructure": extract_spatial_structure(model),
        "spaces": extract_spaces(model, area_scale, volume_scale, length_scale),
        "structuralElements": extract_structural_elements(model, length_scale, area_scale),
        "openings": extract_openings(model, length_scale),
        "mepElements": extract_mep_elements(model, length_scale),
        "materials": extract_materials(model),
        "quantitySummary": {},
        "parseTimeMs": 0
    }

    result["quantitySummary"] = build_quantity_summary(result, model)
    result["parseTimeMs"] = int((time.time() - start) * 1000)

    return result


def extract_header(model) -> dict:
    """
    Faili metainfo.
    model.schema → "IFC2X3", "IFC4"
    model.header.file_name → IfcFileInfo objekt
    """
    header = model.header
    file_name = header.file_name if header else None
    file_desc = header.file_description if header else None

    return {
        "schemaVersion": model.schema,
        "fileName": file_name.name if file_name else "",
        "timestamp": file_name.time_stamp if file_name else "",
        "author": file_name.author[0] if file_name and file_name.author else "",
        "organization": file_name.organization[0] if file_name and file_name.organization else "",
        "originatingSystem": file_name.originating_system if file_name else "",
        "preprocessor": file_name.preprocessor_version if file_name else "",
        "description": file_desc.description[0] if file_desc and file_desc.description else ""
    }


def extract_project(model) -> dict:
    """IFCPROJECT info."""
    projects = model.by_type("IfcProject")
    if not projects:
        return {}
    p = projects[0]
    return {
        "guid": p.GlobalId,
        "name": p.Name or "",
        "description": p.Description or "",
        "phase": p.Phase or ""
    }


def extract_spatial_structure(model) -> dict:
    """
    Hierarhia: Project → Site → Building → Storey
    """
    structure = {
        "sites": [],
        "buildings": [],
        "storeys": []
    }

    for site in model.by_type("IfcSite"):
        structure["sites"].append({
            "guid": site.GlobalId,
            "name": site.Name or "",
            "description": site.Description or ""
        })

    for building in model.by_type("IfcBuilding"):
        structure["buildings"].append({
            "guid": building.GlobalId,
            "name": building.Name or "",
            "description": building.Description or "",
            "address": extract_address(building)
        })

    for storey in model.by_type("IfcBuildingStorey"):
        structure["storeys"].append({
            "guid": storey.GlobalId,
            "name": storey.Name or "",
            "elevation": float(storey.Elevation or 0)
        })

    return structure


def extract_address(building) -> str:
    """Ehitise aadress kui olemas."""
    addr = building.BuildingAddress if hasattr(building, "BuildingAddress") else None
    if not addr:
        return ""
    parts = []
    if addr.AddressLines:
        parts.extend(addr.AddressLines)
    if addr.Town:
        parts.append(addr.Town)
    if addr.PostalCode:
        parts.append(addr.PostalCode)
    if addr.Country:
        parts.append(addr.Country)
    return ", ".join(str(p) for p in parts if p)


def extract_spaces(model, area_scale, volume_scale, length_scale) -> list:
    """
    IFCSPACE → ruumid.
    Iga ruumi kohta: nimi, pindala, maht, korrus.
    """
    spaces = []
    for space in model.by_type("IfcSpace"):
        # Leia korrus kuhu ruum kuulub
        container = util_element.get_container(space)
        storey_name = container.Name if container else ""

        # Quantity set'idest pindala ja maht
        qsets = get_quantity_sets(space)
        area = qsets.get("NetFloorArea", qsets.get("GrossFloorArea", 0))
        volume = qsets.get("NetVolume", qsets.get("GrossVolume", 0))
        height = qsets.get("Height", qsets.get("FinishCeilingHeight", 0))

        # Ühikute konversioon
        if area and area_scale:
            area = area * area_scale
        if volume and volume_scale:
            volume = volume * volume_scale
        if height and length_scale:
            height = height * length_scale

        spaces.append({
            "guid": space.GlobalId,
            "name": space.Name or "",
            "longName": space.LongName or "",
            "storeyName": storey_name,
            "area": round(area, 2) if area else 0,
            "volume": round(volume, 2) if volume else 0,
            "height": round(height, 2) if height else 0
        })

    return spaces


def extract_structural_elements(model, length_scale, area_scale) -> list:
    """
    Seinad, plaadid, talad, sambad, katused, trepid jne.
    IFC tüübid: IfcWall, IfcWallStandardCase, IfcSlab, IfcColumn, IfcBeam,
                IfcRoof, IfcStair, IfcRamp, IfcRailing, IfcCovering, IfcFooting
    """
    structural_types = [
        "IfcWall", "IfcWallStandardCase", "IfcSlab", "IfcColumn", "IfcBeam",
        "IfcRoof", "IfcStair", "IfcStairFlight", "IfcRamp", "IfcRailing",
        "IfcCovering", "IfcFooting", "IfcPile", "IfcCurtainWall", "IfcPlate"
    ]

    elements = []
    for ifc_type in structural_types:
        try:
            for el in model.by_type(ifc_type):
                elements.append(extract_element_info(el, model, length_scale, area_scale))
        except RuntimeError:
            # Type might not exist in this schema version
            continue

    return elements


def extract_openings(model, length_scale) -> list:
    """Uksed ja aknad."""
    openings = []
    for ifc_type in ["IfcDoor", "IfcWindow"]:
        try:
            for el in model.by_type(ifc_type):
                info = extract_element_info(el, model, length_scale, 1.0)
                # Uksel/aknal on OverallWidth ja OverallHeight otse attributidena
                if hasattr(el, 'OverallWidth') and el.OverallWidth:
                    info["overallWidth"] = round(float(el.OverallWidth) * length_scale * 1000, 0)  # mm
                else:
                    info["overallWidth"] = None
                if hasattr(el, 'OverallHeight') and el.OverallHeight:
                    info["overallHeight"] = round(float(el.OverallHeight) * length_scale * 1000, 0)  # mm
                else:
                    info["overallHeight"] = None
                openings.append(info)
        except RuntimeError:
            continue

    return openings


def extract_mep_elements(model, length_scale) -> list:
    """
    KVVK (MEP) elemendid: torud, kanalid, terminaalid, seadmed.
    """
    mep_types = [
        "IfcPipeSegment", "IfcPipeFitting",
        "IfcDuctSegment", "IfcDuctFitting", "IfcDuctSilencer",
        "IfcFlowSegment", "IfcFlowFitting", "IfcFlowTerminal",
        "IfcFlowController", "IfcFlowMovingDevice", "IfcFlowStorageDevice",
        "IfcFlowTreatmentDevice", "IfcEnergyConversionDevice",
        "IfcUnitaryEquipment",
        "IfcFireSuppressionTerminal", "IfcSanitaryTerminal",
        "IfcWasteTerminal", "IfcStackTerminal", "IfcAirTerminal",
        "IfcAirTerminalBox",
        "IfcBoiler", "IfcChiller", "IfcCoil", "IfcCondenser",
        "IfcEvaporator", "IfcHeatExchanger", "IfcHumidifier",
        "IfcPump", "IfcFan", "IfcCompressor",
        "IfcValve", "IfcDamper", "IfcFilter"
    ]

    elements = []
    for ifc_type in mep_types:
        try:
            for el in model.by_type(ifc_type):
                info = extract_element_info(el, model, length_scale, 1.0)

                # MEP-spetsiifilised väljad property set'idest
                psets = util_element.get_psets(el)
                all_props = {}
                for pset_name, props in psets.items():
                    all_props.update(props)

                info["systemType"] = classify_mep_system(el, all_props)
                info["nominalDiameter"] = find_prop(all_props, ["NominalDiameter", "Nominal Diameter", "Diameter", "DN", "Size"])
                info["innerDiameter"] = find_prop(all_props, ["InnerDiameter", "Inner Diameter", "Inside Diameter"])
                info["outerDiameter"] = find_prop(all_props, ["OuterDiameter", "Outer Diameter", "Outside Diameter"])
                info["length"] = find_prop(all_props, ["Length", "Pikkus"])
                info["flowRate"] = find_prop(all_props, ["FlowRate", "Flow Rate", "VolumetricFlowRate", "Vooluhulk"])
                info["pressure"] = find_prop(all_props, ["WorkingPressure", "Pressure", "Rõhk", "DesignPressure"])
                info["predefinedType"] = get_predefined_type(el)

                # Pikkuse konversioon meetriteks
                if info["length"] and length_scale:
                    try:
                        info["length"] = round(float(info["length"]) * length_scale, 3)
                    except (ValueError, TypeError):
                        info["length"] = None

                elements.append(info)
        except RuntimeError:
            # Mõni IFC tüüp ei pruugi antud skeemas eksisteerida (IFC2x3 vs IFC4)
            continue

    return elements


def extract_element_info(el, model, length_scale, area_scale) -> dict:
    """
    Ühe elemendi baasinfo.
    Kasutatakse nii structural, opening kui MEP elementide jaoks.
    """
    # Tüübi nimi (IfcRelDefinesByType kaudu)
    el_type = util_element.get_type(el)
    type_name = el_type.Name if el_type else ""

    # Korrus
    container = util_element.get_container(el)
    storey_name = ""
    if container:
        if container.is_a("IfcBuildingStorey"):
            storey_name = container.Name or ""
        elif hasattr(container, 'Name'):
            storey_name = container.Name or ""

    # Materjal
    material = util_element.get_material(el)
    material_name = extract_material_name(material)

    # Property set'id — kõik ühte flat dict'i
    psets = util_element.get_psets(el)
    flat_props = {}
    for pset_name, props in psets.items():
        for k, v in props.items():
            if v is not None and v != "" and k != "id":
                flat_props[k] = convert_value(v)

    # Quantity set'id
    qsets = get_quantity_sets(el)

    return {
        "entityId": el.id(),
        "guid": el.GlobalId,
        "ifcType": el.is_a(),
        "name": el.Name or "",
        "description": el.Description or "",
        "objectType": el.ObjectType if hasattr(el, "ObjectType") else "",
        "tag": el.Tag if hasattr(el, "Tag") else "",
        "typeName": type_name,
        "storeyName": storey_name,
        "materialName": material_name,
        "properties": flat_props,
        "quantities": {k: round(float(v), 4) for k, v in qsets.items() if isinstance(v, (int, float))}
    }


def extract_material_name(material) -> str:
    """Extract material name(s) from various IFC material types."""
    if not material:
        return ""

    try:
        if material.is_a("IfcMaterial"):
            return material.Name or ""
        elif material.is_a("IfcMaterialLayerSet"):
            layers = material.MaterialLayers or []
            return ", ".join([l.Material.Name for l in layers if l.Material and l.Material.Name])
        elif material.is_a("IfcMaterialLayerSetUsage"):
            lset = material.ForLayerSet
            if lset:
                layers = lset.MaterialLayers or []
                return ", ".join([l.Material.Name for l in layers if l.Material and l.Material.Name])
        elif material.is_a("IfcMaterialList"):
            mats = material.Materials or []
            return ", ".join([m.Name for m in mats if m.Name])
        elif material.is_a("IfcMaterialConstituentSet"):
            constituents = material.MaterialConstituents or []
            return ", ".join([c.Material.Name for c in constituents if c.Material and c.Material.Name])
        elif material.is_a("IfcMaterialProfileSet"):
            profiles = material.MaterialProfiles or []
            return ", ".join([p.Material.Name for p in profiles if p.Material and p.Material.Name])
        elif material.is_a("IfcMaterialProfileSetUsage"):
            pset = material.ForProfileSet
            if pset:
                profiles = pset.MaterialProfiles or []
                return ", ".join([p.Material.Name for p in profiles if p.Material and p.Material.Name])
    except Exception:
        pass

    return ""


def get_quantity_sets(el) -> dict:
    """
    Elemendi quantity set'id (IfcElementQuantity).
    Kasuta util_element.get_psets(el, qtos_only=True)
    """
    try:
        qtos = util_element.get_psets(el, qtos_only=True)
        flat = {}
        for qto_name, quantities in qtos.items():
            for k, v in quantities.items():
                if v is not None and k != "id":
                    flat[k] = v
        return flat
    except Exception:
        return {}


def classify_mep_system(el, props: dict) -> str:
    """
    Tuvastab MEP elemendi süsteemi tüübi.

    Prioriteet:
    1. Property set'idest: "System Type", "System Name", "System Classification"
    2. Revit "Identity Data" → "System Type"
    3. Elemendi ObjectType / Name analüüs
    4. Materjali põhjal
    5. Elemendi IFC tüübi põhjal (fallback)

    Return: "heating", "water_supply", "sewage", "ventilation", "fire_suppression", "electrical", "unknown"
    """
    # 1. Check property sets for system classification
    system_keys = ["System Type", "SystemType", "System Name", "SystemName",
                   "System Classification", "SystemClassification", "System",
                   "Revit System Type", "MEP System Type"]

    system_value = ""
    for key in system_keys:
        if key in props and props[key]:
            system_value = str(props[key]).lower()
            break

    # Also check ObjectType and Name from element
    obj_type = el.ObjectType.lower() if hasattr(el, 'ObjectType') and el.ObjectType else ""
    name = el.Name.lower() if el.Name else ""
    combined_text = f"{system_value} {obj_type} {name}"

    # 2. Pattern matching

    # Estonian patterns
    # Heating: "küte", "kütte", "radiaator", "põrand.*küte"
    heating_patterns_et = [
        r'\bküte\b', r'\bkütte\b', r'\bradiaator', r'\bpõrand.*küte',
        r'\bsoojus', r'\bkatel', r'\bboiler'
    ]
    # Water supply: "vesi", "vee", "veetoru", "soojavesi", "külmavesi", "SVK", "EVK"
    water_patterns_et = [
        r'\bvesi\b', r'\bvee\b', r'\bveetoru', r'\bsoojavesi', r'\bkülmavesi',
        r'\bsvk\b', r'\bevk\b', r'\bkraani', r'\bdušš', r'\bsegisti'
    ]
    # Sewage: "kanal", "kanalisatsioon", "äravool", "reovesi", "K1", "K2"
    sewage_patterns_et = [
        r'\bkanal', r'\bkanalisatsioon', r'\bäravool', r'\breovesi',
        r'\bk1\b', r'\bk2\b', r'\bwc\b', r'\btrapid'
    ]
    # Ventilation: "vent", "ventilatsioon", "õhk"
    vent_patterns_et = [
        r'\bvent', r'\bventilatsioon', r'\bõhk', r'\bväljatõmme',
        r'\bsissepuhke', r'\brecup'
    ]

    # English patterns
    # Heating
    heating_patterns_en = [
        r'\bheating\b', r'\bradiator', r'\bhw\b', r'\blthw\b', r'\bmthw\b',
        r'\bunderfloor\b', r'\bboiler', r'\bheat\b', r'\bhtg\b'
    ]
    # Water supply
    water_patterns_en = [
        r'\bwater\b', r'\bsupply\b', r'\bcw\b', r'\bdhw\b', r'\bcold water\b',
        r'\bhot water\b', r'\bdomestic\b', r'\bpotable\b', r'\bfresh\b'
    ]
    # Sewage
    sewage_patterns_en = [
        r'\bsewer', r'\bsewage\b', r'\bdrain', r'\bwaste\b', r'\bsoil\b',
        r'\bsw\b', r'\bfw\b', r'\brw\b', r'\bsanitary\b', r'\bstorm'
    ]
    # Ventilation
    vent_patterns_en = [
        r'\bvent', r'\bair\b', r'\bsupply air\b', r'\bextract\b', r'\bexhaust\b',
        r'\bsa\b', r'\bea\b', r'\bra\b', r'\boa\b', r'\bahv\b', r'\bhvac\b',
        r'\bduct\b', r'\bair conditioning\b', r'\bac\b'
    ]
    # Fire suppression
    fire_patterns = [
        r'\bfire\b', r'\bsprinkler', r'\btulekustut', r'\bfire suppress'
    ]

    # Check patterns in order of priority
    for pattern in heating_patterns_et + heating_patterns_en:
        if re.search(pattern, combined_text, re.IGNORECASE):
            return "heating"

    for pattern in vent_patterns_et + vent_patterns_en:
        if re.search(pattern, combined_text, re.IGNORECASE):
            return "ventilation"

    for pattern in sewage_patterns_et + sewage_patterns_en:
        if re.search(pattern, combined_text, re.IGNORECASE):
            return "sewage"

    for pattern in water_patterns_et + water_patterns_en:
        if re.search(pattern, combined_text, re.IGNORECASE):
            return "water_supply"

    for pattern in fire_patterns:
        if re.search(pattern, combined_text, re.IGNORECASE):
            return "fire_suppression"

    # 3. IFC type fallback
    ifc_type = el.is_a()

    if "Duct" in ifc_type or "AirTerminal" in ifc_type:
        return "ventilation"
    if "Fan" in ifc_type or "Damper" in ifc_type:
        return "ventilation"
    if "SanitaryTerminal" in ifc_type:
        return "water_supply"
    if "WasteTerminal" in ifc_type or "StackTerminal" in ifc_type:
        return "sewage"
    if "FireSuppression" in ifc_type:
        return "fire_suppression"
    if "Boiler" in ifc_type or "HeatExchanger" in ifc_type:
        return "heating"
    if "Chiller" in ifc_type or "Coil" in ifc_type:
        return "ventilation"

    # Check for pipe - try to determine type from diameter or material
    if "Pipe" in ifc_type:
        # Large diameter pipes (> DN100) are often heating or main supply
        diameter = find_prop(props, ["NominalDiameter", "Diameter", "DN"])
        if diameter:
            try:
                d = float(diameter)
                if d > 100:  # DN > 100 mm
                    return "heating"  # Often heating mains
            except (ValueError, TypeError):
                pass

    return "unknown"


def find_prop(props: dict, possible_keys: list):
    """Leia property väärtus mitmest võimalikust nimest."""
    for key in possible_keys:
        # Case-insensitive otsing
        for pk, pv in props.items():
            if pk.lower() == key.lower():
                return convert_value(pv)
    return None


def get_predefined_type(el) -> str:
    """Elemendi PredefinedType (enum)."""
    try:
        pt = el.PredefinedType
        return str(pt) if pt else ""
    except AttributeError:
        return ""


def convert_value(v):
    """Konverteeri property väärtus JSON-serialiseeritavaks."""
    if v is None:
        return None
    if isinstance(v, (int, float, str, bool)):
        return v
    if isinstance(v, (list, tuple)):
        return [convert_value(x) for x in v]
    return str(v)


def extract_materials(model) -> list:
    """
    Kõik materjalid ja nende kasutusstatistika.
    """
    material_usage = defaultdict(int)

    for el in model.by_type("IfcProduct"):
        mat = util_element.get_material(el)
        if mat:
            try:
                if mat.is_a("IfcMaterial"):
                    material_usage[mat.Name or "Unknown"] += 1
                elif mat.is_a("IfcMaterialLayerSet"):
                    for layer in (mat.MaterialLayers or []):
                        if layer.Material:
                            material_usage[layer.Material.Name or "Unknown"] += 1
                elif mat.is_a("IfcMaterialLayerSetUsage"):
                    lset = mat.ForLayerSet
                    if lset:
                        for layer in (lset.MaterialLayers or []):
                            if layer.Material:
                                material_usage[layer.Material.Name or "Unknown"] += 1
                elif mat.is_a("IfcMaterialList"):
                    for m in (mat.Materials or []):
                        material_usage[m.Name or "Unknown"] += 1
                elif mat.is_a("IfcMaterialConstituentSet"):
                    for c in (mat.MaterialConstituents or []):
                        if c.Material:
                            material_usage[c.Material.Name or "Unknown"] += 1
                elif mat.is_a("IfcMaterialProfileSet"):
                    for p in (mat.MaterialProfiles or []):
                        if p.Material:
                            material_usage[p.Material.Name or "Unknown"] += 1
            except Exception:
                continue

    return [
        {"name": name, "usageCount": count}
        for name, count in sorted(material_usage.items(), key=lambda x: -x[1])
    ]


def build_quantity_summary(result: dict, model) -> dict:
    """
    Kokkuvõte kogustest hinnapäringu jaoks.
    """
    summary = {
        "totalElements": len(result["structuralElements"]) + len(result["openings"]),
        "totalMepElements": len(result["mepElements"]),
        # Structural counts
        "wallCount": sum(1 for e in result["structuralElements"] if "Wall" in e["ifcType"]),
        "slabCount": sum(1 for e in result["structuralElements"] if "Slab" in e["ifcType"]),
        "columnCount": sum(1 for e in result["structuralElements"] if "Column" in e["ifcType"]),
        "beamCount": sum(1 for e in result["structuralElements"] if "Beam" in e["ifcType"]),
        "doorCount": sum(1 for e in result["openings"] if "Door" in e["ifcType"]),
        "windowCount": sum(1 for e in result["openings"] if "Window" in e["ifcType"]),
        # MEP counts
        "pipeSegmentCount": sum(1 for e in result["mepElements"] if "PipeSegment" in e["ifcType"]),
        "pipeFittingCount": sum(1 for e in result["mepElements"] if "PipeFitting" in e["ifcType"]),
        "ductSegmentCount": sum(1 for e in result["mepElements"] if "DuctSegment" in e["ifcType"]),
        "ductFittingCount": sum(1 for e in result["mepElements"] if "DuctFitting" in e["ifcType"]),
        "flowTerminalCount": sum(1 for e in result["mepElements"] if "Terminal" in e["ifcType"]),
        "valveCount": sum(1 for e in result["mepElements"] if "Valve" in e["ifcType"]),
        "pumpCount": sum(1 for e in result["mepElements"] if "Pump" in e["ifcType"]),
        "boilerCount": sum(1 for e in result["mepElements"] if "Boiler" in e["ifcType"]),
        "fanCount": sum(1 for e in result["mepElements"] if "Fan" in e["ifcType"]),
        "filterCount": sum(1 for e in result["mepElements"] if "Filter" in e["ifcType"]),
        # Aggregated
        "totalPipeLength": round(sum(e.get("length", 0) or 0 for e in result["mepElements"] if "PipeSegment" in e["ifcType"]), 2),
        "totalDuctLength": round(sum(e.get("length", 0) or 0 for e in result["mepElements"] if "DuctSegment" in e["ifcType"]), 2),
        "totalWallArea": round(sum(
            e.get("quantities", {}).get("NetSideArea", e.get("quantities", {}).get("GrossSideArea", 0))
            for e in result["structuralElements"] if "Wall" in e["ifcType"]
        ), 2),
        "totalSlabArea": round(sum(
            e.get("quantities", {}).get("NetArea", e.get("quantities", {}).get("GrossArea", 0))
            for e in result["structuralElements"] if "Slab" in e["ifcType"]
        ), 2),
    }

    # By system
    system_counts = defaultdict(int)
    system_pipe_length = defaultdict(float)
    system_duct_length = defaultdict(float)
    for e in result["mepElements"]:
        st = e.get("systemType", "unknown")
        system_counts[st] += 1
        if "PipeSegment" in e["ifcType"] and e.get("length"):
            system_pipe_length[st] += e["length"]
        if "DuctSegment" in e["ifcType"] and e.get("length"):
            system_duct_length[st] += e["length"]

    summary["elementCountBySystem"] = dict(system_counts)
    summary["pipeLengthBySystem"] = {k: round(v, 2) for k, v in system_pipe_length.items()}
    summary["ductLengthBySystem"] = {k: round(v, 2) for k, v in system_duct_length.items()}

    # By material
    mat_counts = defaultdict(int)
    for e in result["mepElements"] + result["structuralElements"] + result["openings"]:
        mat = e.get("materialName", "")
        if mat:
            mat_counts[mat] += 1
    summary["elementCountByMaterial"] = dict(mat_counts)

    return summary


# ─── MAIN ───────────────────────────────────────────────

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python3 extract_ifc.py <input.ifc> <output.json>", file=sys.stderr)
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2]

    try:
        data = extract(input_path)
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        # Prindi kokkuvõte stderr'i (logi jaoks)
        summary = data["quantitySummary"]
        print(f"OK: {summary.get('totalElements', 0)} structural + {summary.get('totalMepElements', 0)} MEP elements, "
              f"parsed in {data['parseTimeMs']}ms", file=sys.stderr)
        sys.exit(0)
    except Exception as e:
        import traceback
        print(f"ERROR: {type(e).__name__}: {str(e)}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)
