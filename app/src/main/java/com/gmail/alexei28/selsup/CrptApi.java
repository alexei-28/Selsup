package com.gmail.alexei28.selsup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// POST http://127.0.0.1:8081/api/v3/lk/documents/create
// Payload(json) is in the request body.
public final class CrptApi {
    private static final int WEB_SERVER_PORT = 8081;
    private static final String PATH_SPEC = "/api/v3/lk/documents/create";
    private static final Logger LOGGER = LoggerFactory.getLogger(CrptApi.class);

    public static void main(String[] args) {
        new CrptApi(WEB_SERVER_PORT);
    }

    public CrptApi(int port) {
        try {
            final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
            final Server server = new Server(port);

            ServletContextHandler servletContextHandler = new ServletContextHandler();
            servletContextHandler.addServlet(new ServletHolder(new DocumentServlet(Duration.ofSeconds(5), 5, gson)), PATH_SPEC);
            server.setHandler(servletContextHandler);

            server.start();
            LOGGER.debug("Server start on port {}", WEB_SERVER_PORT);
            server.join();
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
    }

    private final class DocumentServlet extends HttpServlet {
        private final Gson gson;
        private final long durationLimitSec;
        private final int requestLimit;
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private LocalDateTime startDateTime = LocalDateTime.now();
        private static final String ERROR_NUMBER_REQUESTS_EXCEEDS_LIMIT = "The number of requests exceeds the limit. Try later";

        public DocumentServlet(Duration durationLimit, int requestLimit, Gson gson) {
            this.durationLimitSec = durationLimit.toSeconds();
            this.requestLimit = requestLimit;
            this.gson = gson;
            LOGGER.debug("durationLimitSec = {}, requestLimit = {}", this.durationLimitSec, this.requestLimit);
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            try {
                requestCount.incrementAndGet();
                if (requestCount.get() == 1) {
                    startDateTime = LocalDateTime.now();
                    LOGGER.debug("requestCount = {} -> RESET startDateTime = {}", requestCount.get(), startDateTime);
                }
                long diffSec = ChronoUnit.SECONDS.between(startDateTime, LocalDateTime.now());
                if (diffSec <= durationLimitSec) {
                    if (requestCount.get() > requestLimit) {

                        LOGGER.warn("REJECT request (diffSec({}) <= durationLimitSec({}), requestCount = {} > requestLimit = {}) -> {}",
                                diffSec, durationLimitSec, requestCount, requestLimit, ERROR_NUMBER_REQUESTS_EXCEEDS_LIMIT);
                        throw new RequestsExceedsLimitException(ERROR_NUMBER_REQUESTS_EXCEEDS_LIMIT);
                    }
                } else {
                    requestCount.set(0);
                    LOGGER.debug("RESET requestCount = {} (diffSec({}) > durationLimitSec({}))", requestCount.get(), diffSec, durationLimitSec);
                }
                String payload = request.getReader().lines().collect(Collectors.joining());
                Document document = gson.fromJson(payload, Document.class);
                LOGGER.debug("RESPONSE OK, diffSec = {}, requestCount = {}, doc_id = {}", diffSec, requestCount.get(), document.doc_id);
                response.getWriter().println("{\n" + "\"Status\": \"OK\"\n" + "}");
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage());
                String errorMessage = "General error";
                if (ex instanceof JsonSyntaxException) {
                    errorMessage = "Incorrect document";
                } else if (ex instanceof RequestsExceedsLimitException) {
                    errorMessage = ex.getMessage();
                }
                response.getWriter().println("{\n" +
                        "\"Status\": \"Error\",\n" +
                        "\"Message\": \"" + errorMessage + "\"\n" +
                        "}");
            }
        }
    }

    private final class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        private final class Description {
            private String participantInn;
        }

        private final class Product {
            private String certificate_document;
            private String certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private String production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;
        }
    }

    private class RequestsExceedsLimitException extends Exception {
        public RequestsExceedsLimitException(String message) {
            super(message);
        }
    }
}
