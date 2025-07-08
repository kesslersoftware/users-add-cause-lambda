package com.boycottpro.usercauses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.models.Causes;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.models.UserCauses;
import com.boycottpro.usercauses.model.AddCausesForm;
import com.boycottpro.usercauses.model.Reason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class AddUserCausesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AddUserCausesHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public AddUserCausesHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            AddCausesForm inputForm = objectMapper.readValue(event.getBody(), AddCausesForm.class);
            String userId = inputForm.getUser_id();
            for(Reason reasons : inputForm.getCauses()) {
                if(!userIsFollowingCause(userId, reasons.getCause_id())) {
                    addUserCause(userId,reasons.getCause_id(), reasons.getCause_desc());
                }
            }
            return response(200, "All causes added successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return response(500, "Transaction failed: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String body)  {
        ResponseMessage message = new ResponseMessage(status,body,
                body);
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
    }

    private boolean addUserCause(String userId, String causeId, String CauseDesc) {
        String now = Instant.now().toString();
        // Build the item map
        Map<String, AttributeValue> item = Map.ofEntries(
                Map.entry("user_id", AttributeValue.fromS(userId)),
                Map.entry("cause_id", AttributeValue.fromS(causeId)),
                Map.entry("cause_desc", AttributeValue.fromS(CauseDesc)),
                Map.entry("timestamp", AttributeValue.fromS(now))
        );

        // Construct the PutItemRequest
        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName("user_causes")
                .item(item)
                .build();

        // Execute the insert
        dynamoDb.putItem(putRequest);
        return true;
    }

    private boolean userIsFollowingCause(String userId, String causeId) {
        QueryRequest request = QueryRequest.builder()
                .tableName("user_causes")
                .keyConditionExpression("user_id = :uid AND cause_id = :cid")
                .expressionAttributeValues(Map.of(
                        ":uid", AttributeValue.builder().s(userId).build(),
                        ":cid", AttributeValue.builder().s(causeId).build()
                ))
                .limit(1)
                .build();

        return !dynamoDb.query(request).items().isEmpty();
    }

}