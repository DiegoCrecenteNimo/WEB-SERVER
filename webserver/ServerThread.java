


import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.*;
import java.util.ArrayList;



public class ServerThread extends Thread{
    private Socket socket;
    private boolean isListingDirectory;

    public ServerThread(Socket s, boolean isListingDirectory) {
        this.socket = s;
        this.isListingDirectory = isListingDirectory;
    }

    private static void printAndSend(String line, BufferedOutputStream output) {
        try {
            byte[] bytes = line.getBytes();
            output.write(bytes);
            System.out.print(line);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copyFile(BufferedInputStream src, BufferedOutputStream dest) {
        try {
            String line;
            byte[] buffer = new byte[1024];
            int aux;
            while ((aux = src.read(buffer)) != -1) {
                dest.write(buffer, 0, aux);
            }
        }catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (src != null) src.close();
                if (dest != null) dest.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private String listDirectory(Path path, String host) throws IOException {
        File file = new File(path.toString());
        Path base = Paths.get("./recurses/");

        File[] files = file.listFiles();
        StringBuilder sb = new StringBuilder("<html>\n<head>\n" +
                            "<title>\n" + file.getName() +
                            "\n</title>\n</head>\n<body>\n" +
                            "<h3>" + file.getName() + "</h3>\n");
        for (File f : files) {
            sb.append("<a href = \"").append("").append(f.getName()).append("\">").append(f.getName()).append("</a>\n");
        }
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    public void run() {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());


            System.out.println("*PETITION*"); //Recibir peticion
            ArrayList<String> lines = new ArrayList<>();
            String aux_line = null;
            String ifModifiedSince = null;
            while ((aux_line = input.readLine()) == null || !aux_line.equals("")) {
                if (aux_line != null) {
                    lines.add(aux_line);
                    System.out.println(aux_line);
                    if (aux_line.contains("If-Modified-Since")) ifModifiedSince = aux_line;
                }
            }
            String[] petitionWords = lines.get(0).split(" ");


            System.out.println("\n*RESPONSE*\n"); //Preparar respuesta
            Path objectPath = Paths.get("./recurses" + petitionWords[1]);
            boolean isDirectory = false;
            FileInputStream objectFile = null;
            BasicFileAttributes attributes;
            String responseCode;
            String dynamicHTML = "";
            //Procesar primera linea respuesta y fichero que se enviara
            if(!petitionWords[0].equals("HEAD") && !petitionWords[0].equals("GET") || (!petitionWords[2].equals("HTTP/1.1") && !petitionWords[2].equals("HTTP/1.0"))) {
                objectPath = Paths.get("./recurses/error400.html");
                objectFile = new FileInputStream(objectPath.toString());
                responseCode = "HTTP/1.0 400 Bad Request";
            }else {
                try {
                    attributes = Files.readAttributes(objectPath, BasicFileAttributes.class);
                    if (attributes.isDirectory()) {
                        objectPath = Paths.get(objectPath.toString() + "/index.html");
                    }
                    objectFile = new FileInputStream(objectPath.toString());
                    responseCode = "HTTP/1.0 200 OK";
                } catch (FileNotFoundException e) {
                    if (isListingDirectory) {
                        objectPath = Paths.get(objectPath.toString().replace("/index.html", ""));
                        dynamicHTML = listDirectory(objectPath, socket.getInetAddress() + ":" + socket.getPort());
                        isDirectory = true;
                        //objectFile = null;
                        responseCode = "HTTP/1.0 200 OK";
                    }else {
                        objectPath = Paths.get("./recurses/error403.html");
                        objectFile = new FileInputStream(objectPath.toString());
                        responseCode = "HTTP/1.0 403 Forbbiden";
                    }
                }catch (IOException e) {
                    objectPath = Paths.get("./recurses/error404.html");
                    objectFile = new FileInputStream(objectPath.toString());
                    responseCode = "HTTP/1.0 404 Not Found";
                }
            }
            attributes  = Files.readAttributes(objectPath, BasicFileAttributes.class);


            ZonedDateTime localDate = ZonedDateTime.now(); //Fecha actual
            DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            String dateString = formatter.format(localDate);

            FileTime lastModifiedFileTime = attributes.lastModifiedTime(); //Fecha de la ultima vez que se modifico el fichero
            ZonedDateTime lastModifiedTime = lastModifiedFileTime.toInstant().atZone(ZoneId.systemDefault());

            if (ifModifiedSince != null) {
                //Preparamos fecha de cabecera if-modified-date
                String modifiedLine = ifModifiedSince.replace("If-Modified-Since: ", "");
                ZonedDateTime ifModifiedDate = ZonedDateTime.parse(modifiedLine, formatter);
                if (ifModifiedDate.isBefore(lastModifiedTime))
                    responseCode = "HTTP/1.0 304 Not Modified";
            }

            //Enviamos los datos de la cabecera
            printAndSend(responseCode + "\r\n", output);
            printAndSend("Date: " + dateString + "\r\n" , output);
            printAndSend("Server: ASUS vivobook (Unix)\r\n", output);
            printAndSend("Content-Length: " + attributes.size() + "\r\n", output);
            printAndSend("Content-Type: " + Files.probeContentType(objectPath) + "\r\n", output);
            printAndSend("Last-Modified: " + lastModifiedTime.format(formatter) + "\r\n", output);


            printAndSend("\r\n", output);
            if (!petitionWords[0].equals("HEAD")) { //Si el metodo es HEAD no enviamos el cuerpo de entidad
                if (isDirectory) {
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output));
                    writer.write(dynamicHTML);
                    writer.close();
                }else {
                    BufferedInputStream responseBodyBuffer = new BufferedInputStream(objectFile);
                    copyFile(responseBodyBuffer, output);
                }
            }

            //Registrar petición en fichero server_log.txt
            Path logPath = Paths.get("./server_log.txt");
            BufferedWriter wrInFile = new BufferedWriter(new FileWriter(logPath.toString(), true));
            wrInFile.write("Request: " + lines.get(0) + " | " +
                            "Client: " + socket.getInetAddress() + " | " +
                            "Date: " + dateString + " | " +
                            "Response: " + responseCode +  " | " +
                            "Content-Length: " + attributes.size() + "\n");

            output.close();
            input.close();
            wrInFile.close();
            System.out.println("\n");

        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
