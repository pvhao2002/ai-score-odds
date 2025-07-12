package com.app.kira.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonUtil {

    private static final Gson gson = new Gson();

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return gson.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            System.err.println("Gson parsing error: " + e.getMessage());
            return null;
        }
    }

    public static String toJson(Object obj) {
        try {
            return gson.toJson(obj);
        } catch (Exception e) {
            System.err.println("Gson serialization error: " + e.getMessage());
            return null;
        }
    }
}

