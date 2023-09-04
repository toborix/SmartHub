import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;


public class SmartHub {


    static class LEB128 {
        public static long decodeUnsigned(byte[] data) {
            long value = 0;
            long shift = 0;
            for (byte b : data) {
                value |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
//        System.out.println("value: "+ value);
            return value;
        }
    }


    private static final Set<Integer> allowed_cmds = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5, 6));
    private final String url;

    public SmartHub(String name) {
        int type = 1;
        int status = 0;
        int src = 0;
        this.url = "http://localhost:9999";
    }


    public static String cleanData(String data) {
        return data.replaceAll("[^a-zA-Z0-9]", "");
    }


    public static String getCmdCommand(int cmd) {
        Map<Integer, String> commands = new HashMap<>();
        commands.put(1, "WHOISHERE");
        commands.put(2, "IAMHERE");
        commands.put(3, "GETSTATUS");
        commands.put(4, "STATUS");
        commands.put(5, "SETSTATUS");
        commands.put(6, "TICK");

        if (!allowed_cmds.contains(cmd)) {
            return "UNKNOWN";
        }
        return commands.get(cmd);
    }

    public static String getDevName(int devType) {


        Map<Integer, String> names = new HashMap<>();
        names.put(0, "Unknown");
        names.put(1, "SmartHub");
        names.put(2, "EnvSensor");
        names.put(3, "Switch");
        names.put(4, "Lamp");
        names.put(5, "Socket");
        names.put(6, "Clock");

        if (!allowed_cmds.contains(devType)) {
            return "UNKNOWN";
        }


        return names.get(devType);
    }


    public static List<Map<String, Object>> decodeBytesStream(List<byte[]> dataArray) {
        List<Map<String, Object>> structs = new ArrayList<>();

        for (byte[] req : dataArray) {

            Map<String, Object> struct = new LinkedHashMap<>();
            struct.put("length", ByteBuffer.wrap(req, 0, 1).get() & 0xFF);
            struct.put("src", LEB128.decodeUnsigned(Arrays.copyOfRange(req, 1, 3)));
            struct.put("dst", LEB128.decodeUnsigned(Arrays.copyOfRange(req, 3, 5)));
            struct.put("serial", LEB128.decodeUnsigned(Arrays.copyOfRange(req, 5, 6)));
            struct.put("dev_type", LEB128.decodeUnsigned(Arrays.copyOfRange(req, 6, 7)));
            struct.put("cmd", LEB128.decodeUnsigned(Arrays.copyOfRange(req, 7, 8)));


            Long cmdLong = (Long) struct.get("cmd");
            int cmd = cmdLong.intValue();

            Long devTypeLong = (Long) struct.get("dev_type");
            int devType = devTypeLong.intValue();

            switch (getDevName(devType)) {

                case "SmartHub":
                    switch (getCmdCommand(cmd)) {
                        case "WHOISHERE":
                            break;
                        case "IAMHERE":
                            break;
                    }

                case "Clock":
                    switch (getCmdCommand(cmd)) {

                        case "IAMHERE":

                            String dev_name = new String(req, 8, req.length - 8, StandardCharsets.US_ASCII);
                            Map<String, Object> cmd_body_struct = new LinkedHashMap<>();
                            cmd_body_struct.put("dev_name", cleanData(dev_name));
                            struct.put("cmd_body", cmd_body_struct);
                            break;
                        case "TICK":
                            cmd_body_struct = new LinkedHashMap<>();
                            cmd_body_struct.put("timestamp", LEB128.decodeUnsigned(Arrays.copyOfRange(req, 8, 14)));
                            struct.put("cmd_body", cmd_body_struct);
                            break;

                    }

                default:
                    break;
//
            }

            struct.put("crc8", ByteBuffer.wrap(req, req.length - 1, 1).get() & 0xFF);
            structs.add(struct);
        }

        return structs;
    }

    public static List<Integer> getLengthIndexes(String data) {
        byte[] decoded = Base64.getUrlDecoder().decode(data);
        int _length = ByteBuffer.wrap(decoded, 0, 1).get() & 0xFF;
        int totalLength = _length;
        List<Integer> indices = new ArrayList<>();
        indices.add(_length);

        while (totalLength < decoded.length) {

            _length = ByteBuffer.wrap(decoded, totalLength, 1).get() & 0xFF;
            totalLength += _length + 4;
            if (_length != 0) {
                indices.add(_length);
            }
        }

        return indices;
    }


    public static List<byte[]> extractReqData(String data) {
        List<Integer> sizes = getLengthIndexes(data);
        byte[] decoded = Base64.getUrlDecoder().decode(data);
        List<byte[]> substrings = new ArrayList<>();

        for (Integer i : sizes) {
            byte[] substring = Arrays.copyOfRange(decoded, 0, i + 2);
            substrings.add(substring);

            // Update 'decoded' starting from position 'i + 2'
            if (i + 2 <= decoded.length) {
                decoded = Arrays.copyOfRange(decoded, i + 2, decoded.length);
            } else {
                break;
            }
        }
        return substrings;
    }


    public String sendRequest(byte[] data, String url, int dst) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Handle success
                return (response.body());
            } else if (response.statusCode() == 204) {
                System.exit(0);
            } else {
                System.exit(99);
            }
        } catch (IOException | InterruptedException e) {
            System.exit(99); // not zero indicates abnormal termination
        }
        return null;
    }

    // For the method extractAlphanumericBytes you can use regex in Java
    public static List<Byte> extractAlphanumericBytes(String string) {
        List<Byte> alphanumericBytes = new ArrayList<>();
        Pattern p = Pattern.compile("\\w");

        for (Character ch : string.toCharArray()) {
            Matcher m = p.matcher(ch.toString());
            if (m.matches()) {
                alphanumericBytes.add((byte) ch.charValue());
            }
        }

        return alphanumericBytes;
    }


    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            System.exit(99);
        }
        // First argument is always a String
        String arg1 = args[0];
        // Check if second argument is an integer
        int arg2;
        try {
            arg2 = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.exit(99);
            return; // Not necessary, but some IDEs don't understand System.exit() stops execution
        }


        SmartHub smartHub = new SmartHub("SmartHub");


        String HUB_WHOISHERE = "DAH_fwEBAQVIVUIwMeE";
        String response = smartHub.sendRequest(HUB_WHOISHERE.getBytes(), args[0], Integer.parseInt(args[1]));


        List<byte[]> _data = SmartHub.extractReqData(response);
        List<Map<String, Object>> _decoded = SmartHub.decodeBytesStream(_data);

    }
}

