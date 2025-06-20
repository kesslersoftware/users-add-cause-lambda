package com.boycottpro.usercauses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.UserCauses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddUserCausesHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private AddUserCausesHandler handler;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testAddsUserCauseWhenNotFollowing() throws Exception {
        UserCauses input = new UserCauses("user123", "cause456", "Environmental", null);

        // Mock user is not following
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(List.of()).build());

        // Mock successful put
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(input));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("user123"));
        assertTrue(response.getBody().contains("cause456"));
        assertTrue(response.getBody().contains("Environmental"));

        verify(dynamoDb, times(1)).putItem(any(PutItemRequest.class));
    }

    @Test
    void testSkipsInsertIfAlreadyFollowing() throws Exception {
        UserCauses input = new UserCauses("user123", "cause456", "Environmental", null);

        // Mock existing record
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(List.of(Map.of("user_id", AttributeValue.fromS("user123")))).build());

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(input));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());
        verify(dynamoDb, never()).putItem((PutItemRequest) any());
    }

    @Test
    void testReturns500OnJsonParseError() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withBody("not-json");

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
    }

    @Test
    void testAddsTimestampOnInsert() throws Exception {
        UserCauses input = new UserCauses("user123", "cause456", "Rights", null);

        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(List.of()).build());
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(input));

        APIGatewayProxyResponseEvent response = handler.handleRequest(request, context);

        assertEquals(200, response.getStatusCode());

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(captor.capture());

        Map<String, AttributeValue> item = captor.getValue().item();
        assertNotNull(item.get("timestamp"));
        assertDoesNotThrow(() -> Instant.parse(item.get("timestamp").s()));
    }
}
