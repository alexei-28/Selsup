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
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
            servletContextHandler.addServlet(new ServletHolder(new DocumentServlet(Duration.ofSeconds(5), 3, gson)), PATH_SPEC);
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
        private final List<Document> list = new CopyOnWriteArrayList<>();

        public DocumentServlet(Duration durationLimit, int requestLimit, Gson gson) {
            this.durationLimitSec = durationLimit.toSeconds();
            this.requestLimit = requestLimit;
            this.gson = gson;
            LOGGER.debug("durationLimitSec = {}, requestLimit = {}, this = {} ", this.durationLimitSec, this.requestLimit, this);
        }

        // E.g. POST http://127.0.0.1:8081/api/v3/lk/documents/create
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            try {
                requestCount.incrementAndGet();
                if (requestCount.get() == 1) {
                    startDateTime = LocalDateTime.now();
                    LOGGER.debug("requestCount = {} -> RESET_TIMER, startDateTime = {}", requestCount.get(), startDateTime);
                }
                long diffSec = ChronoUnit.SECONDS.between(startDateTime, LocalDateTime.now());
                if (diffSec <= durationLimitSec) {
                    if (requestCount.get() > requestLimit) {
                        LOGGER.warn("REJECT request (diffSec({}) <= durationLimitSec({}), requestCount = {} > requestLimit = {}) -> The number of requests exceeds the limit. Try later",
                                diffSec, durationLimitSec, requestCount, requestLimit);
                        throw new RequestsExceedsLimitException("The number of requests exceeds the limit. Try later");
                    }
                } else {
                    requestCount.set(0);
                    LOGGER.debug("RESET requestCount = {} (diffSec > durationLimitSec)", requestCount);
                }
                String payload = request.getReader().lines().collect(Collectors.joining());
                Document document = gson.fromJson(payload, Document.class);
                list.add(document);
                LOGGER.debug("RESPONSE OK, requestCount = {}", requestCount);
                response.getWriter().println("{\n" + "\"Status\": \"OK\"\n" + "}");
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage());
                String errorMessage = ex.getMessage();
                if (ex instanceof JsonSyntaxException) {
                    errorMessage = "Incorrect document";
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Document document = (Document) o;
            return Objects.equals(doc_id, document.doc_id) && Objects.equals(production_type, document.production_type);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(doc_id);
        }

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
