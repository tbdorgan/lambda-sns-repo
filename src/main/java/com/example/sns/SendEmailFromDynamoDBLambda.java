package com.example.sns;

import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.*;

public class SendEmailFromDynamoDBLambda implements RequestHandler<SNSEvent, Void> {

    private final AmazonSimpleEmailService sesClient = AmazonSimpleEmailServiceClient.builder().build();
    private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        context.getLogger().log("SNS Event received by SendEmailFromDynamoDBLambda");

        for (SNSEvent.SNSRecord record : event.getRecords()) {
            String message = record.getSNS().getMessage();
            context.getLogger().log("Message: " + message);

            // Assuming `message` is the `employeeId` sent in the SNS message
            String employeeId = message;

            // Initialize DynamoDB client
            DynamoDB dynamoDB = new DynamoDB(client);

            String dynamoTableName = System.getenv("DDB_TABLE_NAME");
            // Get the table from DynamoDB
            Table table = dynamoDB.getTable(dynamoTableName);

            // Get item from DynamoDB
            GetItemSpec spec = new GetItemSpec().withPrimaryKey("employeeId", employeeId);
            Map<String, Object> returnedItem = null;
            try {
                returnedItem = table.getItem(spec).asMap();
            } catch (Exception e) {
                context.getLogger().log("Unable to read item: " + e.getMessage());
                return null;
            }

            if (returnedItem != null) {
                String email = (String) returnedItem.get("email");

                // Send email via SES
                sendEmail(email, "CSV File Processed",
                        "Hello, the CSV file for employee " + employeeId + " has been processed.");
            }
        }

        return null;
    }

    private void sendEmail(String recipientEmail, String subject, String body) {
        try {
            SendEmailRequest request = new SendEmailRequest()
                    .withSource("toshack.desai@gmail.com") // Verified email address in SES
                    .withDestination(new Destination().withToAddresses(recipientEmail))
                    .withMessage(new Message()
                            .withSubject(new Content().withData(subject))
                            .withBody(new Body().withText(new Content().withData(body))));

            sesClient.sendEmail(request);
            System.out.println("Email sent to " + recipientEmail);
        } catch (Exception e) {
            System.out.println("Error sending email: " + e.getMessage());
        }
    }
}
