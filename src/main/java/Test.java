import protoc.hello.HelloWorldRequest;
import protoc.hello.HelloWorldResponse;
import rpc.RpcClient;
import rpc.ServiceFinder;
import service.HelloService;
;

public class Test {
    public static void main(String[] args) {
        RpcClient client = new RpcClient(new ServiceFinder());
        HelloService service = client.newService(HelloService.class);
        HelloWorldResponse hello = service.hello(HelloWorldRequest.newBuilder().setMsg("hello").build());
        System.out.println(hello.getRes());
    }
}