package org.flossware.classloader.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Processes REST API responses in various formats.
 * Handles binary, Base64-encoded, and JSON-embedded class data.
 */
public class ResponseProcessor {
    private static final String JSON_DATA_FIELD = "\"data\":\"";
    private static final String JSON_CONTENT_FIELD = "\"content\":\"";

    /**
     * Response format enumeration for REST APIs.
     */
    public enum ResponseFormat {
        /** Binary class bytes directly in response body */
        BINARY,
        /** Class bytes as Base64 in a JSON field */
        BASE64_JSON_FIELD,
        /** Direct response (same as BINARY) */
        DIRECT
    }

    /**
     * Processes the response data according to the specified format.
     *
     * @param responseData The raw response data
     * @param responseFormat The format to parse
     * @return The processed class bytecode
     * @throws IOException if processing fails
     */
    public byte[] processResponse(byte[] responseData, ResponseFormat responseFormat) throws IOException {
        Objects.requireNonNull(responseData, "responseData cannot be null");
        Objects.requireNonNull(responseFormat, "responseFormat cannot be null");
        switch (responseFormat) {
            case BINARY:
            case DIRECT:
                return responseData;
            case BASE64_JSON_FIELD:
                String json = new String(responseData, StandardCharsets.UTF_8);
                String base64Data = extractBase64FromJson(json);
                return Base64.getDecoder().decode(base64Data);
            default:
                return responseData;
        }
    }

    private String extractBase64FromJson(String json) throws IOException {
        int start = json.indexOf(JSON_DATA_FIELD);
        if (start == -1) {
            start = json.indexOf(JSON_CONTENT_FIELD);
            if (start == -1) {
                throw new IOException("Cannot find data field in JSON response");
            }
            start += JSON_CONTENT_FIELD.length();
        } else {
            start += JSON_DATA_FIELD.length();
        }

        int end = json.indexOf("\"", start);
        if (end == -1) {
            throw new IOException("Malformed JSON response");
        }

        return json.substring(start, end);
    }
}
