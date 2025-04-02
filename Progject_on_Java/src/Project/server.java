package laba_10_2;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

public class server {
    private static int maxClients = 5;
    private static final Map<String, PrintWriter> clientWriters = new HashMap<>();
    private static final Set<String> admins = new HashSet<>();
    private static final String ADMIN_PASSWORD = "12345123";
    private static volatile boolean running = true;
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        int port = 14434;

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Сервер слушает порт " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    synchronized (clientWriters) {
                        if (clientWriters.size() >= maxClients) {
                            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                            out.println("Сервер перегружен. Попробуйте подключиться позже.");
                            clientSocket.close();
                        } else {
                            new Thread(new ClientHandler(clientSocket)).start();
                        }
                    }
                } catch (SocketException e) {
                    if (!running) {
                        System.out.println("Сервер завершает работу...");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            cleanUp();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private String clientName;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                while (true) {
                    String request = in.readLine();
                    if (request != null) {
                        handleRequest(request);
                    }
                }
            } catch (IOException e) {
                System.out.println("Ошибка при обработке клиента " + clientName + ": " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void handleRequest(String request) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new ByteArrayInputStream(request.getBytes("UTF-8")));

                Element root = doc.getDocumentElement();
                String command = root.getElementsByTagName("command").item(0).getTextContent();
                String data = root.getElementsByTagName("data").item(0) != null 
                              ? root.getElementsByTagName("data").item(0).getTextContent() 
                              : "";

                switch (command) {
                    case "setName":
                        setName(data);
                        break;
                    case "message":
                        handleMessage(data);
                        break;
                    case "info":
                        sendInfo();
                        break;
                    case "time":
                        sendTime();
                        break;
                    case "getAdmin":
                        grantAdmin(data); // Передача данных (например, пароля)
                        break;
                    default:
                        out.println("Неизвестная команда: " + command);
                }
            } catch (Exception e) {
                out.println("Ошибка обработки запроса: некорректный формат XML.");
            }
        }

        private void grantAdmin(String password) {
            if (ADMIN_PASSWORD.equals(password)) {
                admins.add(clientName);
                out.println("Вы получили права администратора!");
            } else {
                out.println("Неверный пароль для получения прав администратора.");
            }
        }

        private void sendInfo() {
            String info = "Разработчики: Казаченок Михаил, Тюев Захар.";
            out.println(info);
            saveMessageToFile("Система", info);
        }

        private void sendTime() {
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            String currentTime = "Текущая дата и время: " + formatter.format(new Date());
            out.println(currentTime);
            saveMessageToFile("Система", currentTime);
        }

        private void handleCommand(String message) {
            if (admins.contains(clientName)) {
                switch (message.toLowerCase()) {
                    case "shutdown":
                        broadcast("Сервер закрывается по команде администратора " + clientName);
                        shutdownServer();
                        return;
                    case "kick":
                        out.println("Введите имя пользователя для исключения: ");
                        try {
                            String userToKick = in.readLine();
                            kickUser(userToKick);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return;
                    case "setmax":
                        out.println("Введите новое максимальное количество клиентов: ");
                        try {
                            int newMaxClients = Integer.parseInt(in.readLine());
                            synchronized (clientWriters) {
                                maxClients = newMaxClients;
                                out.println("Максимальное количество клиентов изменено на " + newMaxClients);
                            }
                        } catch (NumberFormatException | IOException e) {
                            out.println("Ошибка при установке нового максимального количества клиентов: " + e.getMessage());
                        }
                        return;
                    default:
                        out.println("Неизвестная команда админа.");
                }
            } else {
                out.println("У вас нет прав для выполнения этой команды.");
            }
        }

        private void setName(String name) {
            synchronized (clientWriters) {
                if (clientWriters.containsKey(name) || name.trim().isEmpty()) {
                    out.println("Это имя уже занято. Пожалуйста, выберите другое имя.");
                } else {
                    clientName = name;
                    clientWriters.put(name, out);
                    out.println("Добро пожаловать, " + clientName + "!");
                    System.out.println(clientName + " подключен");
                }
            }
        }

        private void handleMessage(String message) {
            if (clientName != null) {
                if (message.startsWith("/")) {
                    handleCommand(message.substring(1));
                } else {
                    System.out.println(clientName + ": " + message);
                    broadcast(clientName + ": " + message);
                    saveMessageToFile(clientName, message);
                }
            }
        }

        private void saveMessageToFile(String clientName, String message) {
            try {
                File file = new File("message_history.xml");
                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                Document doc;

                if (file.exists() && file.length() > 0) {
                    doc = docBuilder.parse(file);
                    doc.getDocumentElement().normalize();
                } else {
                    doc = docBuilder.newDocument();
                    Element rootElement = doc.createElement("messages");
                    doc.appendChild(rootElement);
                }

                Element root = doc.getDocumentElement();
                Element messageElement = doc.createElement("message");
                Element clientElement = doc.createElement("client");
                clientElement.appendChild(doc.createTextNode(clientName));
                messageElement.appendChild(clientElement);
                Element textElement = doc.createElement("text");
                textElement.appendChild(doc.createTextNode(message));
                messageElement.appendChild(textElement);
                root.appendChild(messageElement);

                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(file);
                transformer.transform(source, result);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters.values()) {
                    writer.println(message);
                }
            }
        }

        private void kickUser(String userName) {
            synchronized (clientWriters) {
                PrintWriter kickedOut = clientWriters.get(userName);
                if (kickedOut != null) {
                    kickedOut.println("Вас исключил администратор.");
                    kickedOut.close();
                    clientWriters.remove(userName);
                    System.out.println("Пользователь " + userName + " был исключен.");
                } else {
                    out.println("Пользователь " + userName + " не найден.");
                }
            }
        }

        private void closeConnection() {
            if (clientName != null) {
                synchronized (clientWriters) {
                    clientWriters.remove(clientName);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println(clientName + " отключен");
            }
        }

        private void shutdownServer() {
            running = false;
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters.values()) {
                    writer.println("Сервер закрывается.");
                    writer.close();
                }
                clientWriters.clear();
            }
            try {
                serverSocket.close();
                System.out.println("Сервер завершил работу.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void cleanUp() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Сервер завершил работу.");
    }
}