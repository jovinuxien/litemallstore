package org.linlinjava.litemall.library;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public final  class SerializerUtils {


    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new ParameterNamesModule())
            .addModule(new Jdk8Module())
            .addModule(new JavaTimeModule())
            .build();

    private SerializerUtils() {

    }

    public static byte[] serializeToJsonByte(Object object) {
        try {
            return objectMapper.writeValueAsBytes(object);
        }catch (JsonProcessingException jse){
            throw new RuntimeException(jse.getMessage(), jse);
        }
    }

    public static <T> T deserializeFromJsonBytes(byte[] bytes, Class<T> valueType) {

        try {
            return objectMapper.readValue(bytes, valueType);
        }catch (IOException ioe){
            throw new RuntimeException(ioe.getMessage(), ioe);
        }
    }

    public static Event[] deserializeEventFromJsonBytes(final byte[] jsonBytes){
        try {
           return objectMapper.readValue(jsonBytes, Event[].class);
        }catch(IOException ioe){
            throw new RuntimeException(ioe.getMessage(), ioe);
        }
    }

    public static HashMap<String, byte[]> deserializeEventsMetadata(final byte[] metadata){
        final var tr = new TypeReference<HashMap<String, byte[]>>(){};

        try{
            return objectMapper.readValue(metadata, tr);
        }catch(IOException ioe){
            throw new RuntimeException(ioe.getMessage(), ioe);
        }
    }

    public static byte[] serializeEventsMetadata(final HashMap<String, byte[]> metadata){
        try {
            final var valueAsString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
            return valueAsString.getBytes(StandardCharsets.UTF_8);
        }catch (JsonProcessingException jse){
            throw new RuntimeException(jse.getMessage(), jse);
        }
    }
}
