import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpHeaders;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    // ������� ����� ������������� ��� ��
    private final String BASE_URL = "https://ismp.crpt.ru/api/v3";
    // ������������������ �����
    private String authToken = "";
    // ���������� �������, � ������� �������������� ���������� ��������
    private final TimeUnit TIME_UNIT;
    // ������������ ���������� �������� ��-���������, ���� � ������������ ������ ������������ ��������
    private final int DEFAULT_REQUEST_LIMIT = 1;
    // ������� ������� ������� �������� �������� ��� �������� ������ ��������
    private final ArrayBlockingQueue<Long> REQUEST_TIME_QUEUE;
    // ��������� �������� � ������ JSON
    private final Converter jsonConverter = new JsonConverter();



    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.TIME_UNIT = timeUnit;
        if (requestLimit < 1) requestLimit = DEFAULT_REQUEST_LIMIT;
        REQUEST_TIME_QUEUE = new ArrayBlockingQueue<>(requestLimit);
    }



    // ����� �������� ��������� ��� ����� � ������ ������, �������������� � ��.
    // � ���������� ����������: �������� � �������.
    public String createDocRF(Document document, String signature) {
        return createDoc(document, signature, DocumentFormat.MANUAL,
                Type.LP_INTRODUCE_GOODS, jsonConverter);
    }

    // ������ ����� �������� ����������
    // URL: /api/v3/lk/documents/create
    // �����: POST
    private String createDoc(Document document, String signature,
                           DocumentFormat documentFormat, Type type, Converter converter) {
        // URL ��� �������� ���������
        final String CREATE_DOCUMENT_URL = BASE_URL.concat("/lk/documents/create");
        // �������� ���������� ������ ��������
        checkRequestLimit();
        // ������������ ���� �������
        String documentEncoded = encodeBase64(converter.convert(document));
        String signatureEncoded = encodeBase64(signature);
        Body body = new Body(documentFormat, documentEncoded, signatureEncoded, type);
        // ���������� ������� � ��������� ����������
        Content result = null;
        try {
            result = Request.Post(CREATE_DOCUMENT_URL)
                    .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .bodyString(jsonConverter.convert(body), ContentType.APPLICATION_JSON)
                    .execute().returnContent();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result == null ? "" : result.asString();
    }

    // ����� ��������� ����������� ���������� �������� � �������� ��������� �������.
    // ����� ������� ������� ������� ����������� � ������ �������������� �������,
    // ������� requestLimit. ���������� ������� ���� ������� �� ���������.
    // ���� ������� ���������, ����������� ����������� �������� ������� ��������.
    // �������� ���������� ������� ��������, ���� � ������� �������� ������ �������
    // � ������� ������� ������ ������� ������ ��� �������� �������� TIME_UNIT.
    private void checkRequestLimit() {
        // ���� ���������� ���������� � ������� (����� �������� ��������)
        while (!REQUEST_TIME_QUEUE.offer(Instant.now().toEpochMilli())) {
            // ������� ������� � ������� ������� �������� �������
            long oldestRequestTimestamp = REQUEST_TIME_QUEUE.peek();
            // �����, ��������� � ������� �������� �������
            long elapsedTime = Instant.now().toEpochMilli() - oldestRequestTimestamp;
            // ���� � ������� �������� ������� ������ ������, ��� �������� TIME_UNIT,
            // �� ��� ����� �������, ����� ����� ���������� �����.
            if (elapsedTime >= TIME_UNIT.toMillis(1)) {
                REQUEST_TIME_QUEUE.remove(oldestRequestTimestamp);
            } else {
                try {
                    Thread.sleep(TIME_UNIT.toMillis(1) - elapsedTime);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // ����� ����������� � base64
    private String encodeBase64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes());
    }



    @Getter
    @Setter
    public static class Body {
        private DocumentFormat documentFormat;
        private String productDocument;
        private ProductGroup productGroup;
        private String signature;
        private Type type;

        public Body(DocumentFormat documentFormat, String productDocument,
                    String signature, Type type) {
            this.documentFormat = documentFormat;
            this.productDocument = productDocument;
            this.signature = signature;
            this.type = type;
        }
    }

    public interface Converter {
        String convert(Object o);
    }

    public static class JsonConverter implements Converter {
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public String convert(Object o) {
            try {
                return objectMapper.writeValueAsString(o);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Getter
    @Setter
    public static class Document {
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

        public Document(String doc_id, String doc_status, String doc_type, String owner_inn,
                        String participant_inn, String producer_inn, String production_date,
                        String production_type, String reg_date) {
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.reg_date = reg_date;
        }
    }

    @Getter
    @Setter
    public static class Description {
        private String participantInn;

        public Description(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    @Getter
    @Setter
    public static class Product {
        private CertificateDocument certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        public Product(String owner_inn, String producer_inn,
                       String production_date, String tnved_code) {
            this.owner_inn = owner_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.tnved_code = tnved_code;
        }
    }

    public enum CertificateDocument {
        CONFORMITY_CERTIFICATE,
        CONFORMITY_DECLARATION
    }

    public enum Type {
        LP_INTRODUCE_GOODS,
        LP_INTRODUCE_GOODS_CSV,
        LP_INTRODUCE_GOODS_XML
    }

    public enum DocumentFormat {
        MANUAL,
        XML,
        CSV
    }

    public enum ProductGroup {
        CLOTHES,
        SHOES,
        TOBACCO,
        PERFUMERY,
        TIRES,
        ELECTRONICS,
        PHARMA,
        MILK,
        BICYCLE,
        WHEELCHAIRS
    }
}
