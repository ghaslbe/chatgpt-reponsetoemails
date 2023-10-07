import javax.mail.*;
import javax.mail.search.FlagTerm;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Scanner;

/*

  javac -cp .:json-20230618.jar:javax.mail-1.6.2.jar:activation-1.1.1.jar ChatGPTMail.java
  java -cp .:json-20230618.jar:javax.mail-1.6.2.jar:activation-1.1.1.jar ChatGPTMail

*/


public class ChatGPTMail {

    public static void main(String[] args) {
        String host = "mail.yourmail.com"; // Zum Beispiel "imap.gmail.com" für Gmail
        String username = "me@gmx.com";
        String password = ".PPa";

        try {
            Properties properties = new Properties();
            properties.put("mail.store.protocol", "imap");
            properties.put("mail.imap.host", host);
            properties.put("mail.imap.port", "993");
            properties.put("mail.imap.ssl.enable", "true");

            Session session = Session.getDefaultInstance(properties);
            Store store = session.getStore();
            store.connect(username, password);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // Ungelesene E-Mails abrufen
            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            if (messages.length == 0) {
                System.out.println("Keine ungelesenen E-Mails gefunden.");
            } else {
                Folder readFolder = store.getFolder("INBOX/Gelesen"); // Name des Ordners, in den die E-Mails verschoben werden sollen
                if (!readFolder.exists()) {
                    boolean success = readFolder.create(Folder.HOLDS_MESSAGES);
                    if (success) {
                        System.out.println("Ordner 'Gelesen' wurde erfolgreich erstellt.");
                    } else {
                        System.out.println("Fehler: Der Ordner 'Gelesen' konnte nicht erstellt werden.");
                    }
                }

                for (Message message : messages) {
                    
                    System.out.println("Betreff: " + message.getSubject());
                    InternetAddress fromAddress = (InternetAddress) message.getFrom()[0];
                    System.out.println("Name: " + fromAddress.getPersonal());
                    System.out.println("E-Mail: " + fromAddress.getAddress());

                    System.out.println("Inhalt: " + getTextFromMessage(message));

                    
                    //---
                    
                    String frage = ""+replaceSpecialCharacters(fromAddress.getPersonal())+" schreibt: "+replaceSpecialCharacters(getTextFromMessage(message));

                    String vorgabe = "Du bist Kollege Gerlinde Gruenbaum aus der Personalabteilung. Antworte auf die E-Mail höflich und teile dem Absender mit, dass die Daten entsprechend weitergegeben werden.";
                           vorgabe += "Wenn es sich um eine Krankmeldung handelt, sage ihm er benötigt ab dem dritten Tag eine ärtzliche Bescheinigung.";

                    String input = vorgabe+" Hier ist der Text der E-Mail:"+frage+" ";
                    System.out.println(input);

                    String replymessage = chatGPT(input);

                    
                    // Antwort-Nachricht erstellen und im Drafts-Ordner ablegen
                        Message replyMessage = createReply(message, session, replymessage);
                        Folder draftsFolder = store.getFolder("Drafts");
                        if (!draftsFolder.exists()) {
                            draftsFolder.create(Folder.HOLDS_MESSAGES);
                        }
                        draftsFolder.appendMessages(new Message[]{replyMessage});
                        System.out.println("Antwort-Nachricht im 'Drafts'-Ordner abgelegt.");
                    
                    
                    //---
                    
                    
                    // E-Mail als gelesen markieren
                    message.setFlag(Flags.Flag.SEEN, true);

                    // E-Mail in den 'Gelesen'-Ordner verschieben
                    inbox.copyMessages(new Message[]{message}, readFolder);
                    message.setFlag(Flags.Flag.DELETED, true);
                }

                inbox.expunge(); // Löscht E-Mails, die zum Löschen markiert sind
            }

            inbox.close(false);
            store.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
     private static String getTextFromMessage(Message message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof MimeMultipart) {
            MimeMultipart multipart = (MimeMultipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain")) {
                    return (String) bodyPart.getContent();
                }
            }
        }
        return "";
    }
    
    private static Message createReply(Message original, Session session, String txtvorlage) throws MessagingException {
        MimeMessage replyMessage = (MimeMessage) original.reply(false);  // false bedeutet, dass nicht an alle geantwortet wird
        replyMessage.setFrom(new InternetAddress(original.getRecipients(Message.RecipientType.TO)[0].toString()));
        replyMessage.setText(txtvorlage);  // "Hallo" in den Body der E-Mail schreiben
    return replyMessage;
    }
    
    
    
     public static String chatGPT(String prompt) {
       String url = "https://api.openai.com/v1/chat/completions";
       String apiKey = "sk-4ABWm..........";
       String model = "gpt-3.5-turbo";

       try {
           URL obj = new URL(url);
           HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
           connection.setRequestMethod("POST");
           connection.setRequestProperty("Authorization", "Bearer " + apiKey);
           connection.setRequestProperty("Content-Type", "application/json");

           // The request body
           String body = "{\"model\": \"" + model + "\", \"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}]}";
           connection.setDoOutput(true);
           OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
           writer.write(body);
           writer.flush();
           writer.close();

           // Response from ChatGPT
           BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
           String line;

           StringBuffer response = new StringBuffer();

           while ((line = br.readLine()) != null) {
               response.append(line);
           }
           br.close();

           System.out.println("\n\n"+response+"\n\n");


        JSONObject jobj = new JSONObject(""+response+"");

        System.out.println(jobj.toString());
        

        // Einzelne Felder extrahieren
        String id = jobj.getString("id");
        String object = jobj.getString("object");
        long created = jobj.getLong("created");
        String gptmodel = jobj.getString("model");

        // Das "choices" Array und das darin enthaltene "message" Objekt extrahieren
        JSONArray choicesArray = jobj.getJSONArray("choices");
        JSONObject firstChoice = choicesArray.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        String role = message.getString("role");
        String content = message.getString("content");

        // Die "usage" Daten extrahieren
        JSONObject usage = jobj.getJSONObject("usage");
        int promptTokens = usage.getInt("prompt_tokens");
        int completionTokens = usage.getInt("completion_tokens");
        int totalTokens = usage.getInt("total_tokens");

        // Ausgabe
        System.out.println("ID: " + id);
        System.out.println("Object: " + object);
        System.out.println("Created: " + created);
        System.out.println("Model: " + gptmodel);
        System.out.println("Role: " + role);
        System.out.println("Content: " + content);
        System.out.println("Prompt Tokens: " + promptTokens);
        System.out.println("Completion Tokens: " + completionTokens);
        System.out.println("Total Tokens: " + totalTokens);
    
    
        return content;

       } catch (IOException e) {
           throw new RuntimeException(e);
       }
   }

    public static String replaceSpecialCharacters(String input) {
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (!Character.isLetterOrDigit(chars[i])) {
                chars[i] = ' ';
            }
        }
        return new String(chars);
    }
    
    
}
