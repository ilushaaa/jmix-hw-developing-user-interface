package com.sample.airtickets.screen.ticketreservation;

import com.sample.airtickets.app.TicketService;
import com.sample.airtickets.entity.Airport;
import com.sample.airtickets.entity.Flight;
import com.sample.airtickets.entity.Ticket;
import com.sample.airtickets.screen.ticket.TicketEdit;
import io.jmix.core.DataManager;
import io.jmix.core.LoadContext;
import io.jmix.ui.Notifications;
import io.jmix.ui.ScreenBuilders;
import io.jmix.ui.UiComponents;
import io.jmix.ui.app.inputdialog.InputDialog;
import io.jmix.ui.component.*;
import io.jmix.ui.executor.BackgroundTask;
import io.jmix.ui.executor.BackgroundTaskHandler;
import io.jmix.ui.executor.BackgroundWorker;
import io.jmix.ui.executor.TaskLifeCycle;
import io.jmix.ui.model.CollectionContainer;
import io.jmix.ui.screen.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@UiController("TicketReservationScreen")
@UiDescriptor("ticket-reservation-screen.xml")
public class TicketReservationScreen extends Screen {
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private EntityComboBox fromAirportEntityComboBox;
    @Autowired
    private EntityComboBox toAirportEntityComboBox;
    @Autowired
    private DateField departureDateField;
    @Autowired
    private Notifications notifications;
    @Autowired
    private TicketService ticketService;
    @Autowired
    private BackgroundWorker backgroundWorker;
    @Autowired
    private ProgressBar searchProgressBar;
    private BackgroundTaskHandler<Void> updateProgressBarTaskHandler;
    @Autowired
    private Button searchBtn;
    @Autowired
    private CollectionContainer<Flight> flightsDc;
    @Autowired
    private InputDialogFacet ticketFields;
    @Autowired
    private Table<Flight> flightsTable;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private ScreenBuilders screenBuilders;

    @Subscribe("searchBtn")
    public void onSearchBtnClick(final Button.ClickEvent event) {
        Airport fromAirport = (Airport) fromAirportEntityComboBox.getValue();
        Airport toAirport = (Airport) toAirportEntityComboBox.getValue();
        LocalDate departureDate = (LocalDate) departureDateField.getValue();

        if (fromAirport == null && toAirport == null && departureDate == null) {
            notifications.create()
                    .withCaption("Please, enter at least one filter field...")
                    .show();
            return;
        }
        BackgroundTaskHandler<List<Flight>> taskHandler = backgroundWorker.handle(
                new LoadFlightsTask(fromAirport, toAirport, departureDate));
        taskHandler.execute();

        searchProgressBar.setVisible(true);
        searchBtn.setEnabled(false);

        updateProgressBarTaskHandler = backgroundWorker.handle(new UpdateProgressBarTask());
        updateProgressBarTaskHandler.execute();
    }

    private void flightsLoaded(List<Flight> flights) {
        updateProgressBarTaskHandler.cancel();
        searchProgressBar.setVisible(false);
        searchProgressBar.setValue(0d);
        searchBtn.setEnabled(true);

        flightsDc.setItems(flights);
    }

    private void searchTaskCanceled() {
        updateProgressBarTaskHandler.cancel();
        searchProgressBar.setVisible(false);
        searchProgressBar.setValue(0d);
        searchBtn.setEnabled(true);
    }

    @Install(to = "flightsTable.reserve", subject = "columnGenerator")
    private Component flightsTableReserveColumnGenerator(final Flight flight) {
        LinkButton reserveLink = uiComponents.create(LinkButton.class);
        reserveLink.setCaption("Reserve");
        reserveLink.addClickListener(this::reserveTicket);
        return reserveLink;
    }

    private void reserveTicket(Button.ClickEvent e) {
        ticketFields.setDialogResultHandler(new Consumer<InputDialog.InputDialogResult>() {
            @Override
            public void accept(InputDialog.InputDialogResult inputDialogResult) {
                String passengerName = inputDialogResult.getValue("passengerName");
                String passportNumber = inputDialogResult.getValue("passportNumber");
                String telephone = inputDialogResult.getValue("telephone");

                saveTicketReservation(passengerName, passportNumber, telephone);
            }
        });
        ticketFields.show();
    }

    private void saveTicketReservation(String passengerName, String passportNumber, String telephone) {
        Flight selected = flightsTable.getSingleSelected();
        if (selected == null) {
            return;
        }

        Ticket ticket = dataManager.create(Ticket.class);
        ticket.setFlight(selected);
        ticket.setPassengerName(passengerName);
        ticket.setPassportNumber(passportNumber);
        ticket.setTelephone(telephone);

        ticketService.saveTicket(ticket);

        TicketEdit ticketView = screenBuilders.screen(this)
                .withScreenClass(TicketEdit.class)
                .withOpenMode(OpenMode.NEW_TAB)
                .build();
        ticketView.setEntityToEdit(ticket);
        ticketView.show();
    }

    @Install(to = "flightsDl", target = Target.DATA_LOADER)
    private List<Flight> flightsDlLoadDelegate(final LoadContext<Flight> loadContext) {
        return new ArrayList<>();
    }


    class LoadFlightsTask extends BackgroundTask<Integer, List<Flight>> {
        private Airport fromAirport;
        private Airport toAirport;
        private LocalDate departureDate;

        public LoadFlightsTask(Airport fromAirport, Airport toAirport, LocalDate departureDate) {
            super(20, TimeUnit.MINUTES);

            this.fromAirport = fromAirport;
            this.toAirport = toAirport;
            this.departureDate = departureDate;
        }

        @Override
        public List<Flight> run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            taskLifeCycle.publish(1);
            List<Flight> flights = ticketService.searchFlights(fromAirport, toAirport, departureDate);
            taskLifeCycle.publish(100);
            return flights;
        }

        @Override
        public void progress(List<Integer> changes) {
            searchProgressBar.setValue(changes.get(changes.size() - 1) / 100d);
        }

        @Override
        public void done(List<Flight> flights) {
            TicketReservationScreen.this.flightsLoaded(flights);
        }

        @Override
        public void canceled() {
            TicketReservationScreen.this.searchTaskCanceled();
        }
    }

    class UpdateProgressBarTask extends BackgroundTask<Integer, Void> {
        public UpdateProgressBarTask() {
            super(20, TimeUnit.MINUTES);
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            for (int i = 1; i <= 20; i++) {
                Thread.sleep(100);
                taskLifeCycle.publish(i);
            }
            return null;
        }

        @Override
        public void progress(List<Integer> changes) {
            searchProgressBar.setValue(changes.get(changes.size() - 1) / 20d);
        }
    }
}