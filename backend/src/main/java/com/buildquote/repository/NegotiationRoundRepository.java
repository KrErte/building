package com.buildquote.repository;

import com.buildquote.entity.Bid;
import com.buildquote.entity.NegotiationRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NegotiationRoundRepository extends JpaRepository<NegotiationRound, UUID> {

    List<NegotiationRound> findByBidOrderByRoundNumberAsc(Bid bid);

    int countByBid(Bid bid);
}
