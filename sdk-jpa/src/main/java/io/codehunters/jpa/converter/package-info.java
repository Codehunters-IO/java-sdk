/**
 * JPA attribute converters that store values as JSON text with Jackson 3. Prefer Hibernate's native
 * {@code @JdbcTypeCode(SqlTypes.JSON)} when the column is a real {@code json}/{@code jsonb} type
 * and you query inside it; use these for text columns and portable schemas.
 */
package io.codehunters.jpa.converter;
