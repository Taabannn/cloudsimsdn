package org.cloudbus.cloudsim.sdn.example.topogenerators;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhysicalTopologyGeneratorEdge {
    public static void main(String[] argv) {
        startTest();
    }

    public static void startTest() {
        String jsonFileName = "example-edge/edge.physical.me.json";

        int fanout = 2;
        int numPods = 1;    // Total hosts = (fanout^2) * numPods
        int redundancy = 3;    // For aggr and core tier, how many switches are connected.
        double latency0 = 0.1;
        double latency1 = 100.0;
        double latency2 = 200.0;

        long iops = 1000000000L;

        PhysicalTopologyGeneratorEdge reqg = new PhysicalTopologyGeneratorEdge();
        reqg.createMultiLinkTreeTopology(iops, latency0, latency1, latency2, fanout, numPods, redundancy);
        reqg.wrtieJSON(jsonFileName);
    }

    // This creates a 3 layer cannonical tree topology with redundant links on aggr and core layers
    protected void createMultiLinkTreeTopology(long swIops, double latency0, double latency1, double latency2,
                                               int fanout, int numPods, int redundancy) {
        //dcs
        PhysicalTopologyGeneratorEdge.DCSpec[] dcSpecs = new PhysicalTopologyGeneratorEdge.DCSpec[redundancy + 1];
        dcSpecs[0] = addDC("edgedc", "cloud"); dcSpecs[1] = addDC("netcloud", "cloud");
        dcSpecs[2] = addDC("internet", "cloud"); dcSpecs[3] = addDC("net", "network");

        // core, aggregation(gw), edge
        String[] prefixes = new String[redundancy];
        prefixes[0] = "edge"; prefixes[1] = "netcloud"; prefixes[2] = "internet";

        long[] coreIops = new long[redundancy];
        coreIops[0] = 0; coreIops[1] = 1000000000; coreIops[2] = 1000000000;

        long[] bw = new long[redundancy];
        bw[0] = 25000000; bw[1] = 25000000; bw[2] = 2000000000;

        //core switches
        PhysicalTopologyGeneratorEdge.SwitchSpec[] c = new PhysicalTopologyGeneratorEdge.SwitchSpec[redundancy];
        for (int i = 0; i < redundancy; i++) {
            c[i] = addSwitch(prefixes[i] + "core", "core", coreIops[i], 0, 2, bw[i], dcSpecs[i].name);
        }

        //gateway switches
        PhysicalTopologyGeneratorEdge.SwitchSpec[] a = new PhysicalTopologyGeneratorEdge.SwitchSpec[redundancy];
        int numHosts = numPods * fanout * fanout;
        for(int pod = 0; pod < numPods; pod++) {
            for (int i = 0; i < redundancy; i++) {
                a[i] = addSwitch(prefixes[i] + "gw", "gateway", 1000000000, 1, 2, 25000000, "net");
                //a[i] = addSwitch(prefixes[i] + "gw", "gateway", 1000000000, 0, 2, 25000000, dcSpecs[i].name);

                for (int j = 0; j < redundancy; j++)
                    addLink(a[i], c[j], latency2);
            }

            String[] hostPrefixes = new String[fanout];
            hostPrefixes[0] = "user"; hostPrefixes[1] = "host";
            int[][] pes = { {2, 2, 1, 1}, {1, 1, 1, 1} };
            long[][] mips = { {2000, 2000, 1000, 1000}, {30000000, 30000000, 30000000, 30000000} };
            int[][] ram = { {512, 512, 512, 512}, {10240, 10240, 10240, 10240} };
            long[][] storage = { {100, 100, 1000, 1000}, {10000000, 10000000, 10000000, 10000000} };
            for (int i = 0; i < fanout; i++) {
                PhysicalTopologyGeneratorEdge.SwitchSpec e = addSwitch(prefixes[i] + "edge1", "edge", 1000000000, 1, 4, 25000000, dcSpecs[i].name);
                // Add link between aggr - edge
                for (int j = 0; j < redundancy; j++)
                    addLink(a[j], e, latency2);

                for (int j = 0; j < numHosts; j++) {
                    PhysicalTopologyGeneratorEdge.HostSpec h = addHost(prefixes[i] + hostPrefixes[i] + new Integer(j + 1).toString(),
                    pes[i][j], mips[i][j], ram[i][j], storage[i][j], 25000000, dcSpecs[i].name);
                    addLink(e, h, latency0);
                }
            }
        }

        SwitchSpec inter1 = addSwitch("inter1", "intercloud", 1000000000, 0, 2, 25000000, "net");
        SwitchSpec inter2 = addSwitch("inter2", "intercloud", 1000000000, 0, 2, 25000000, "net");
        addLink(inter1, inter2, latency1);
        addLink(a[0], inter1, latency2);
        addLink(a[1], inter2, latency2); // a[1]: internetgw
        addLink(a[2], inter2, latency2); // a[2]: netcloudgw

        SwitchSpec internetEdge = addSwitch("internetedge", "edge", 1000000000, 1, 4, 2000000000, "internet");
        HostSpec internetHost = addHost("internethost", 1024, 30000000, 10240, 10000000, 25000000, "internet");
        addLink(internetEdge, internetHost, latency0);
        addLink(internetEdge, c[redundancy-1], latency0); // c[redundancy-1]: internetcore
    }

    private List<PhysicalTopologyGeneratorEdge.DCSpec> dcs = new ArrayList<>();
    private List<PhysicalTopologyGeneratorEdge.HostSpec> hosts = new ArrayList<>();
    private List<PhysicalTopologyGeneratorEdge.SwitchSpec> switches = new ArrayList<>();
    private List<PhysicalTopologyGeneratorEdge.LinkSpec> links = new ArrayList<>();

    public PhysicalTopologyGeneratorEdge.DCSpec addDC(String name, String type) {
        PhysicalTopologyGeneratorEdge.DCSpec dcSpec =
                new PhysicalTopologyGeneratorEdge.DCSpec(name, type);
        dcs.add(dcSpec);
        return dcSpec;
    }

    public PhysicalTopologyGeneratorEdge.HostSpec addHost(String name, int pe, long mips, int ram, long storage,
                                                          long bw, String datacenterName) {
        PhysicalTopologyGeneratorEdge.HostSpec host = new PhysicalTopologyGeneratorEdge.HostSpec(name, pe, mips, ram,
                storage, bw, datacenterName);
        hosts.add(host);
        return host;
    }

    public PhysicalTopologyGeneratorEdge.SwitchSpec addSwitch(String name, String type, long iops, int upPorts,
                                                              int downPorts, long bw, String datacenterName) {
        PhysicalTopologyGeneratorEdge.SwitchSpec sw = new PhysicalTopologyGeneratorEdge.SwitchSpec(name, type, iops,
                                                                                                    upPorts, downPorts, bw, datacenterName);
        switches.add(sw);
        return sw;
    }


    private void addLink(PhysicalTopologyGeneratorEdge.NodeSpec source, PhysicalTopologyGeneratorEdge.NodeSpec dest, double latency) {
        links.add(new PhysicalTopologyGeneratorEdge.LinkSpec(source.name, dest.name, latency));
    }

    class DCSpec {
        String name;
        String type;

        public DCSpec(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @SuppressWarnings("unchecked")
        JSONObject toJSON() {
            PhysicalTopologyGeneratorEdge.DCSpec o = this;
            JSONObject obj = new JSONObject();
            obj.put("name", o.name);
            obj.put("type", o.type);
            return obj;
        }
    }

    class NodeSpec {
        String name;
        String type;
        long bw;
    }

    class HostSpec extends PhysicalTopologyGeneratorEdge.NodeSpec {
        int pe;
        long mips;
        int ram;
        long storage;
        String datacenterName;

        @SuppressWarnings("unchecked")
        JSONObject toJSON() {
            PhysicalTopologyGeneratorEdge.HostSpec o = this;
            JSONObject obj = new JSONObject();
            obj.put("name", o.name);
            obj.put("type", o.type);
            obj.put("pes", o.pe);
            obj.put("mips", o.mips);
            obj.put("ram", new Integer(o.ram));
            obj.put("storage", o.storage);
            obj.put("bw", o.bw);
            obj.put("datacenter", datacenterName);
            return obj;
        }

        public HostSpec(String name, int pe, long mips, int ram, long storage, long bw, String datacenterName) {
            this.name = name;
            this.pe = pe;
            this.mips = mips;
            this.ram = ram;
            this.storage = storage;
            this.bw = bw;
            this.datacenterName = datacenterName;
            this.type = "host";
        }
    }

    class SwitchSpec extends PhysicalTopologyGeneratorEdge.NodeSpec {
        long iops;
        int upPorts;
        int downPorts;
        String datacenterName;

        @SuppressWarnings("unchecked")
        JSONObject toJSON() {
            PhysicalTopologyGeneratorEdge.SwitchSpec o = this;
            JSONObject obj = new JSONObject();
            obj.put("name", o.name);
            obj.put("type", o.type);
            obj.put("iops", o.iops);
            obj.put("upports", o.upPorts);
            obj.put("downports", o.downPorts);
            obj.put("bw", o.bw);
            obj.put("datacenter", o.datacenterName);
            return obj;
        }

        public SwitchSpec(String name, String type, long iops, int upPorts, int downPorts, long bw, String datacenterName) {
            this.name = name;
            this.type = type;
            this.iops = iops;
            this.upPorts = upPorts;
            this.downPorts = downPorts;
            this.bw = bw;
            this.datacenterName = datacenterName;
        }
    }

    class LinkSpec {
        String source;
        String destination;
        double latency;

        public LinkSpec(String source, String destination, double latency2) {
            this.source = source;
            this.destination = destination;
            this.latency = latency2;
        }

        @SuppressWarnings("unchecked")
        JSONObject toJSON() {
            PhysicalTopologyGeneratorEdge.LinkSpec link = this;
            JSONObject obj = new JSONObject();
            obj.put("source", link.source);
            obj.put("destination", link.destination);
            obj.put("latency", link.latency);
            return obj;
        }
    }

    int vmId = 0;

    @SuppressWarnings("unchecked")
    public void wrtieJSON(String jsonFileName) {
        JSONObject obj = new JSONObject();

        JSONArray dcList = new JSONArray();
        JSONArray nodeList = new JSONArray();
        JSONArray linkList = new JSONArray();

        for (PhysicalTopologyGeneratorEdge.DCSpec o : dcs) {
            dcList.add(o.toJSON());
        }
        for (PhysicalTopologyGeneratorEdge.HostSpec o : hosts) {
            nodeList.add(o.toJSON());
        }
        for (PhysicalTopologyGeneratorEdge.SwitchSpec o : switches) {
            nodeList.add(o.toJSON());
        }

        for (PhysicalTopologyGeneratorEdge.LinkSpec link : links) {
            linkList.add(link.toJSON());
        }

        obj.put("datacenters", dcList);
        obj.put("nodes", nodeList);
        obj.put("links", linkList);

        try {

            FileWriter file = new FileWriter(jsonFileName);
            file.write(obj.toJSONString().replaceAll(",", ",\n"));
            file.flush();
            file.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(obj);
    }
}
