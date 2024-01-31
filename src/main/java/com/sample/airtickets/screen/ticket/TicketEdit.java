package com.sample.airtickets.screen.ticket;

import io.jmix.ui.navigation.Route;
import io.jmix.ui.screen.*;
import com.sample.airtickets.entity.Ticket;

@UiController("Ticket.edit")
@UiDescriptor("ticket-edit.xml")
@EditedEntityContainer("ticketDc")
@Route(path = "tickets/view")
public class TicketEdit extends StandardEditor<Ticket> {
}