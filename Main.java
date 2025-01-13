import java.io.*; // ввод вывод
import java.net.*; // сокеты сеть
import java.util.ArrayList; 
import java.util.Scanner; 

// !!!ПРИ ЗАПУСКЕ НЕОБХОДИМЫ АРГУМЕНТЫ
// ЕСЛИ СЕРВЕР, ТО server [порт(45000)]
// ЕСЛИ КЛИЕНТ, ТО client [ip(127.0.0.1)] [порт(45000)]

public class Main {
    public static void main(String[] args) {
        // Проверяем, что есть хотя бы два аргумента (например, "server 45000" или "client 127.0.0.1 45000")
        if (args.length > 1) {
            // Если первый аргумент — "server", запускаем сервер
            if (args[0].equals("server")) {
                new Server(Integer.parseInt(args[1])); // Создаем сервер на указанном порту
            }
            // Если первый аргумент — "client", запускаем клиента
            else if (args[0].equals("client")) {
                new Client(args[1], Integer.parseInt(args[2])); // Подключаемся к серверу по указанным IP и порту
            }
        }
    }
}

// Соединение с клиентом
class ServerConnectionThread extends Thread {
    boolean connected = true; // Указывает, активно ли соединение
    Socket socket; 
    Server server; 

    ServerConnectionThread(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    //Обработка сообщений от клиента 
    public void run() {
        System.out.println("Ожидание сообщений от клиента (" + this + ")");
        while (this.server.alive) { 
            try {
                // Читаем сообщение от клиента
                BufferedReader dis = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String msg = dis.readLine(); // Получаем строку от клиента
                if (msg == null) { // Клиент отключился
                    server.clients.remove(this); 
                    this.connected = false; 
                    this.server.onClientChange(this); // Сообщаем серверу об изменении
                    return; 
                } else {
                    server.onClientMessage(this, msg); 
                }
            } catch (IOException e) { // Ошибки ввода-вывода
                server.clients.remove(this); 
                this.connected = false; 
                this.server.onClientChange(this); // Сообщаем серверу об изменении
                return; 
            }
        }
    }

    public String toString() {
        return "Клиент: " + this.socket.getInetAddress().toString() + 
               " порт:" + this.socket.getPort() + 
               " подключен:" + this.connected;
    }

    // Отправка сообщения клиенту
    public void sendMessage(String message) {
        try {
            PrintStream ps = new PrintStream(this.socket.getOutputStream()); // Поток для отправки
            ps.println(message); // Отправляем сообщение
            ps.flush(); // Принудительно отправляем данные
        } catch (IOException e) { 
            e.printStackTrace(); //Вывод ошибки
        }
    }
}

//Новые подключения
class ServerConnectionInitThread extends Thread {
    Server server; 
    int port; 


    ServerConnectionInitThread(int port, Server server) {
        this.server = server;
        this.port = port;
    }

    public void run() {
        try {
            ServerSocket ss = new ServerSocket(port); // Создания сокета для порта
            while (this.server.alive) { // 
                Socket socket = ss.accept(); // Ожидаем подключения клиента
                ServerConnectionThread sst = new ServerConnectionThread(socket, this.server); // Создаем поток для нового клиента
                this.server.clients.add(sst); // Добавляем клиента в список активных клиентов
                this.server.onClientChange(sst); // Сообщаем серверу о новом подключении
                sst.start(); // Запускаем поток для обработки сообщений от клиента
            }
        } catch (IOException e) { 
            e.printStackTrace();
        }
    }
}

// Главный класс сервера
class Server {
    public boolean alive = true; 
    public ArrayList<ServerConnectionThread> clients = new ArrayList<>(); 

    // Конструктор сервера
    Server(int port) {
        System.out.println("Запуск сервера: " + port); // Запуск сервера
        new ServerConnectionInitThread(port, this).start(); // Запускаем поток для обработки новых подключений
    }

    // Вызывается при изменении списка клиентов
    public void onClientChange(ServerConnectionThread clientThread) {
        System.out.println("onClientChange: " + clientThread); // Информация о клиенте
        System.out.println("active client list:");

        String clientsList = ""; // Список всех клиентов в текстовом виде
        for (int i = 0; i < clients.size(); i++) {
            clientsList += i + ". " + clients.get(i) + "\r\n";
        }

        for (ServerConnectionThread c : clients) { 
            c.sendMessage("Clients count=" + clients.size()); // Количество клиентов
            c.sendMessage(clientsList); // Отправляем список
        }

        System.out.print(clientsList); // Выводим список в консоль сервера
    }

    // Вызывается при получении сообщения от клиента
    public void onClientMessage(ServerConnectionThread clientThread, String message) {
        System.out.println("Сообщение от (" + clientThread + "): " + message); // Выводим сообщение
        if (message.startsWith("send:")) { // Условие отправки сообщений
            int msgPos = message.indexOf(":", 5); // Находим позицию разделителя
            int clientIndex = Integer.parseInt(message.substring(5, msgPos)); // Извлекаем индекс клиента
            clients.get(clientIndex).sendMessage(message.substring(msgPos + 1)); // Отправляем сообщение указанному клиенту
        }
    }
}

// Обработка входящих сообщений от клиента
class ClientConnectionThread extends Thread {
    Client client; 
    Socket socket; 

    // Прием клиента
    ClientConnectionThread(Client client) {
        this.client = client;
    }

    public void run() {
        try {
            System.out.println("Подключение к серверу " + this.client.server_address + " порт:" + this.client.server_port); 
            socket = new Socket(this.client.server_address, this.client.server_port); // Подключение к серверу
            BufferedReader dis = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Поток для чтения сообщений от сервера
            while (this.client.alive) { 
                String msg = dis.readLine(); // Читаем сообщение
                if (msg == null) return; 
                System.out.println(msg); 
            }
        } catch (IOException e) { 
            e.printStackTrace();
        }
    }
}

// Главный класс клиента
class Client {
    public boolean alive = true; 
    public String server_address; 
    public int server_port; 

    Client(String server_address, int server_port) {
        this.server_address = server_address; // Сохраняем адрес сервера
        this.server_port = server_port; // Сохраняем порт сервера
        ClientConnectionThread cct = new ClientConnectionThread(this); // Создаем поток для подключения к серверу
        cct.start(); // Запускаем поток

        Scanner scanner = new Scanner(System.in); 
        while (true) { // Цикл для отправки сообщений серверу
            String cmd = scanner.nextLine(); 
            try {
                // Отправляем введенную команду серверу через поток вывода
                PrintStream ps = new PrintStream(cct.socket.getOutputStream());
                ps.println(cmd); // Печатаем команду в поток
                ps.flush(); // Очищаем поток, чтобы данные были отправлены сразу
            } catch (IOException e) { 
                e.printStackTrace(); 
                return; 
            }
        }
    }
}
