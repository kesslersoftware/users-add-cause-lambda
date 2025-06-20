package com.boycottpro.usercauses;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.models.Causes;
import com.boycottpro.models.UserCauses;
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
            UserCauses inputForm = objectMapper.readValue(event.getBody(), UserCauses.class);
            String userId = inputForm.getUser_id();
            String causeId = inputForm.getCause_id();
            if(!userIsFollowingCause(userId, causeId)) {
                String causeDesc = inputForm.getCause_desc();
                String now = Instant.now().toString();
                inputForm.setTimestamp(now);
                addUserCause(inputForm);
            }
            String responseBody = objectMapper.writeValueAsString(inputForm);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(responseBody);
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Unexpected server error: " + e.getMessage() + "\"}");
        }
    }

    private boolean addUserCause(UserCauses inputForm) {
        // Build the item map
        Map<String, AttributeValue> item = Map.ofEntries(
                Map.entry("user_id", AttributeValue.fromS(inputForm.getUser_id())),
                Map.entry("cause_id", AttributeValue.fromS(inputForm.getCause_id())),
                Map.entry("cause_desc", AttributeValue.fromS(inputForm.getCause_desc())),
                Map.entry("timestamp", AttributeValue.fromS(inputForm.getTimestamp()))
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