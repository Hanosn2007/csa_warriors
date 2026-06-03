public class WebDebugMain {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        WebDebugServer server = new WebDebugServer(port);
        server.start();
        System.out.println("Web debugger: http://localhost:" + port);
    }
}
