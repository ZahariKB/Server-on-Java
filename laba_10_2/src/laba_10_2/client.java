package laba_10_2;


import java.io.*;
import java.net.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

public class client {
    private static String clientName;

    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 14434;

        try (Socket socket = new Socket(hostname, port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("Подключено к серверу");

            // Установка имени клиента
            while (true) {
                System.out.print("Введите ваше имя: ");
                clientName = stdIn.readLine();
                sendXml(out, "setName", clientName);
                String response = in.readLine();
                if (response != null) {
                    System.out.println(response);
                    break;
                }
            }

            // Поток для чтения сообщений от сервера
            new Thread(() -> {
                String response;
                try {
                    while ((response = in.readLine()) != null) {
                        System.out.println(response);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Основной цикл ввода
            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                if (userInput.equalsIgnoreCase("info") || userInput.equalsIgnoreCase("time")) {
                    sendXml(out, userInput, "");
                } else if (userInput.equalsIgnoreCase("getAdmin")) {
                    System.out.print("Введите пароль администратора: ");
                    String password = stdIn.readLine();
                    sendXml(out, "getAdmin", password); // Отправка пароля
                } else {
                    sendXml(out, "message", userInput);
                }
                if (userInput.equalsIgnoreCase("bye") || userInput.equalsIgnoreCase("exit")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendXml(PrintWriter out, String command, String data) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootElement = doc.createElement("request");
            doc.appendChild(rootElement);

            Element cmdElement = doc.createElement("command");
            cmdElement.appendChild(doc.createTextNode(command));
            rootElement.appendChild(cmdElement);

            // Добавляем элемент data только если не пустой
            if (!data.isEmpty()) {
                Element dataElement = doc.createElement("data");
                dataElement.appendChild(doc.createTextNode(data));
                rootElement.appendChild(dataElement);
            }

            // Преобразуем документ в строку и отправляем
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
            out.println(writer.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}