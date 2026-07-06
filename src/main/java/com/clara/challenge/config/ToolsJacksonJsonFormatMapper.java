package com.clara.challenge.config;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.databind.ObjectMapper;

public class ToolsJacksonJsonFormatMapper implements FormatMapper {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public <T> T fromString(CharSequence string, JavaType<T> javaType, WrapperOptions options) {
    try {
      return MAPPER.readValue(
          string.toString(), MAPPER.getTypeFactory().constructType(javaType.getJavaTypeClass()));
    } catch (Exception e) {
      throw new RuntimeException("Failed to deserialize JSON", e);
    }
  }

  @Override
  public <T> String toString(T value, JavaType<T> javaType, WrapperOptions options) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize JSON", e);
    }
  }
}
