package com.paritytrading.philadelphia.initiator;

import static com.paritytrading.philadelphia.fix42.FIX42Enumerations.*;
import static com.paritytrading.philadelphia.fix42.FIX42MsgTypes.*;
import static com.paritytrading.philadelphia.fix42.FIX42Tags.*;
import static org.jvirtanen.util.Applications.*;

import com.paritytrading.philadelphia.FIXMessage;
import com.paritytrading.philadelphia.FIXValue;
import java.io.IOException;
import java.net.InetSocketAddress;

class TestInitiator {

    private static final String USAGE = "philadelphia-initiator <host> <port> <orders>";

    public static void main(String[] args) {
        if (args.length != 3)
            usage(USAGE);

        try {
            String host   = args[0];
            int    port   = Integer.parseInt(args[1]);
            int    orders = Integer.parseInt(args[2]);

            main(new InetSocketAddress(host, port), orders, host, port);
        } catch (IllegalArgumentException e) {
            usage(USAGE);
        } catch (IOException e) {
            fatal(e);
        }
    }

    private static void main(InetSocketAddress address, int orders, String host, int port) throws IOException {
        System.out.println("Warming up...");

        Thread t[] = new Thread[36];
        Initiator inits[] = new Initiator[37];
        Thread rem = new Thread();
        for (int j = 0; j <= 36; j++) {
          InetSocketAddress addr = new InetSocketAddress(host, port);
          inits[j] = Initiator.open(addr);
        }
        for (int j = 0; j <= 36; j++) {
          long nextClOrdId = 1+j*(orders/36);

          FIXMessage message = inits[j].getTransport().create();

          inits[j].getTransport().prepare(message, OrderSingle);

          FIXValue clOrdId = message.addField(ClOrdID);

          message.addField(HandlInst).setChar(HandlInstValues.AutomatedExecutionNoIntervention);
          message.addField(Symbol).setString("FOO");
          message.addField(Side).setChar(SideValues.Buy);

          FIXValue transactTime = message.addField(TransactTime);
          transactTime.setString(inits[j].getTransport().getCurrentTimestamp());

          message.addField(OrderQty).setFloat(100.00, 2);
          message.addField(OrdType).setChar(OrdTypeValues.Limit);
          message.addField(Price).setFloat(25.50, 2);
          if (j < 36) {
            t[j] = new ParallelRequest(orders/36, inits[j], message);
          } else {
            if (orders%36 == 0) {
              rem = new ParallelRequest(orders%36, inits[j], message);
            }
          }
        }
        for (int j = 0; j < 36; j++) {
          t[j].start();
        }
        if (orders%36 == 0) {
          rem.start();
        }
        for (int i = 0; i < 36; i++) {
          try {
            t[i].join();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        try {
          rem.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        for (int j = 0; j <= 36; j++) {
          inits[j].getHistogram().reset();
        }

        System.out.println("Benchmarking...");

        long start = System.nanoTime();
        for (int j = 0; j <= 36; j++) {
          long nextClOrdId = 1+j*(orders/36);

          FIXMessage message = inits[j].getTransport().create();

          inits[j].getTransport().prepare(message, OrderSingle);

          FIXValue clOrdId = message.addField(ClOrdID);

          message.addField(HandlInst).setChar(HandlInstValues.AutomatedExecutionNoIntervention);
          message.addField(Symbol).setString("FOO");
          message.addField(Side).setChar(SideValues.Buy);

          FIXValue transactTime = message.addField(TransactTime);
          transactTime.setString(inits[j].getTransport().getCurrentTimestamp());

          message.addField(OrderQty).setFloat(100.00, 2);
          message.addField(OrdType).setChar(OrdTypeValues.Limit);
          message.addField(Price).setFloat(25.50, 2);
          if (j < 36) {
            t[j] = new ParallelRequest(orders/36, inits[j], message);
          } else {
            if (orders%36 == 0) {
              rem = new ParallelRequest(orders%36, inits[j], message);
            }
          }
        }
        for (int j = 0; j < 36; j++) {
          t[j].start();
        }
        if (orders%36 == 0) {
          rem.start();
        }
        for (int i = 0; i < 36; i++) {
          try {
            t[i].join();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        try {
          rem.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        long end = System.nanoTime();

        for (int j = 0; j <= 36; j++) {
          inits[j].getTransport().close();
        }

        System.out.printf("Results (n = %d)\n", orders);
        System.out.printf("\n");
        // System.out.printf("   50.00%%: %10.2f µs\n", initiator.getHistogram().getValueAtPercentile( 50.00) / 1000.0);
        // System.out.printf("   90.00%%: %10.2f µs\n", initiator.getHistogram().getValueAtPercentile( 90.00) / 1000.0);
        // System.out.printf("   95.00%%: %10.2f µs\n", initiator.getHistogram().getValueAtPercentile( 95.00) / 1000.0);
        // System.out.printf("   99.00%%: %10.2f µs\n", initiator.getHistogram().getValueAtPercentile( 99.00) / 1000.0);
        // System.out.printf("   99.90%%: %10.2f µs\n", initiator.getHistogram().getValueAtPercentile( 99.90) / 1000.0);
        // System.out.printf("   99.99%%: %10.2f µs\n", initiator.getHistogram().getValueAtPercentile( 99.99) / 1000.0);
        // System.out.printf("  100.00%%: %10.2f µs\n", initiator.getHistogram().getValueAtPercentile(100.00) / 1000.0);
        System.out.printf("  Avg. t/p: %10.2f rps\n", orders / ((end - start) / 1000000000.0));
        System.out.printf("\n");
    }

}

class ParallelRequest extends Thread {
    private int num;
    private Initiator in;
    private FIXMessage mes;
    public ParallelRequest(int numOrders, Initiator i, FIXMessage m) {
      num = numOrders;
      in = i;
      mes = m;
    }

    @Override
    public void run() {
      try {
        for (int i = 0; i < num; i++) {
            in.send(mes);

            in.receive();
            System.out.println("hi");
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
}
