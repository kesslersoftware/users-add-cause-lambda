package com.boycottpro.usercauses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.models.UserCauses;
import com.boycottpro.usercauses.model.AddCausesForm;
import com.boycottpro.usercauses.model.Reason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
        Reason reason = new Reason("cause456", "Environmental");
        List<Reason> causes = new ArrayList<>();
        causes.add(reason);
        AddCausesForm input = new AddCausesForm("user123",causes);

        // Mock user is not following
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(List.of()).build());

        // Mock successful put
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(input));
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);


        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);
        ResponseMessage message = objectMapper.readValue(response.getBody(), ResponseMessage.class);
        assertEquals(200, response.getStatusCode());
        assertTrue(message.getMessage().contains("All causes added successfully."));
        verify(dynamoDb, times(1)).putItem(any(PutItemRequest.class));
    }

    @Test
    void testSkipsInsertIfAlreadyFollowing() throws Exception {
        Reason reason = new Reason("cause456", "Environmental");
        List<Reason> causes = new ArrayList<>();
        causes.add(reason);
        AddCausesForm input = new AddCausesForm("user123",causes);
        // Mock existing record
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(List.of(Map.of("user_id", AttributeValue.fromS("user123")))).build());

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withBody(objectMapper.writeValueAsString(input));
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);


        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(200, response.getStatusCode());
        verify(dynamoDb, never()).putItem((PutItemRequest) any());
    }

    @Test
    void testReturns500OnJsonParseError() throws JsonProcessingException {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withBody("not-json");
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);


        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);
        ResponseMessage message = objectMapper.readValue(response.getBody(), ResponseMessage.class);
        assertEquals(500, response.getStatusCode());
        assertTrue(message.getMessage().contains("Transaction failed:"));
    }
}
