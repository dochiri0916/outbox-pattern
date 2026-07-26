package com.dochiri.outboxpattern.cdc.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DebeziumOutboxRecordParserTest {

    private final DebeziumOutboxRecordParser parser = new DebeziumOutboxRecordParser(new ObjectMapper());

    @Test
    @DisplayName("Debezium create 이벤트의 after 데이터를 공통 Outbox 메시지로 변환한다")
    void parse_create_event() {
        // given
        String value = """
                {"payload":{"op":"c","after":{
                  "id":7,"aggregate_type":"POST","aggregate_id":42,
                  "event_type":"POST_FILE_UPLOAD","payload":"{\\"postId\\":42}"
                }}}
                """;

        // when
        DebeziumOutboxRecord result = parser.parse(value);

        // then
        assertEquals("c", result.op());
        assertNotNull(result.after());
        assertEquals("7", result.after().id());
        assertEquals("POST_FILE_UPLOAD", result.after().eventType());
        assertEquals("{\"postId\":42}", result.after().payload());
    }

    @Test
    @DisplayName("삭제 이벤트는 after 메시지를 만들지 않는다")
    void parse_delete_event_without_after() {
        // given
        String value = "{\"payload\":{\"op\":\"d\",\"after\":null}}";

        // when
        DebeziumOutboxRecord result = parser.parse(value);

        // then
        assertEquals("d", result.op());
        assertNull(result.after());
    }
}
