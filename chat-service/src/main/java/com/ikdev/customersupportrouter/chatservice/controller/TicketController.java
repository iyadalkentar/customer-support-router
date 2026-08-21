package com.ikdev.customersupportrouter.chatservice.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import com.ikdev.customersupportrouter.chatservice.dto.TicketResponse;
import com.ikdev.customersupportrouter.chatservice.entity.Ticket;
import com.ikdev.customersupportrouter.chatservice.entity.TicketStatus;
import com.ikdev.customersupportrouter.chatservice.exception.TicketNotFoundException;
import com.ikdev.customersupportrouter.chatservice.repository.TicketRepository;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    /** Caps unfiltered list responses so they don't grow unbounded as tickets accumulate. */
    private static final Pageable MAX_RESULTS = PageRequest.of(0, 200, Sort.unsorted());

    private final TicketRepository ticketRepository;

    public TicketController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/{ticketId}")
    public TicketResponse getTicket(@PathVariable Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        return TicketResponse.from(ticket);
    }

    @GetMapping
    public List<TicketResponse> getTickets(@RequestParam(required = false) TicketStatus status) {
        List<Ticket> tickets = status == null
                ? ticketRepository.findAllByOrderByCreatedAtDesc(MAX_RESULTS)
                : ticketRepository.findByStatusOrderByCreatedAtDesc(status, MAX_RESULTS);
        return tickets.stream().map(TicketResponse::from).toList();
    }
}
