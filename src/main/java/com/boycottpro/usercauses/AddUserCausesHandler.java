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
import com.boycottpro.utilities.JwtUtility;
import com.boycottpro.utilities.Logger;
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
        String sub = null;
        int lineNum = 41;
        try {
            sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) {
            Logger.error(45, sub, "user is Unauthorized");
            return response(401, Map.of("message", "Unauthorized"));
            }
            lineNum = 48;
            AddCausesForm inputForm = objectMapper.readValue(event.getBody(), AddCausesForm.class);
            for(Reason reasons : inputForm.getCauses()) {
                lineNum = 51;
                if(!userIsFollowingCause(sub, reasons.getCause_id())) {
                    lineNum = 53;
                    addUserCause(sub,reasons.getCause_id(), reasons.getCause_desc());
                    lineNum = 55;
                    incrementCauseRecord(reasons.getCause_id());
                }
            }
            lineNum = 59;
            return response(200, Map.of("message",
                    "All causes added successfully."));
        } catch (Exception e) {
            Logger.error(lineNum, sub, e.getMessage());
            return response(500,Map.of("error", "Unexpected server error: " + e.getMessage()) );
        }
    }

    private APIGatewayProxyResponseEvent response(int status, Object body) {
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(body);
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
    private boolean incrementCauseRecord(String causeId) {
        try {
            System.out.println("going to increment companies record");
            dynamoDb.updateItem(UpdateItemRequest.builder()
                    .tableName("causes")
                    .key(Map.of("cause_id", AttributeValue.fromS(causeId)))
                    .updateExpression("SET follower_count = follower_count + :inc")
                    .expressionAttributeValues(Map.of(
                            ":inc", AttributeValue.fromN("1")
                    )).build());
            return true;
        } catch(Exception e) {
            return false;
        }
    }
}