package org.cloudbus.cloudsim.sdn.example.topogenerators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VirtualTopologyGeneratorEdge extends VirtualTopologyGeneratorVmTypes {
    public static void main(String [] argv) {
        //String jsonFileName = "virtual.wiki.complex.json";

        VirtualTopologyGeneratorEdge vmGenerator = new VirtualTopologyGeneratorEdge();
        boolean noscale = false;
        vmGenerator.generateLarge3TierTopologyEdge("example-edge/edge.virtual.me.json", noscale);
    }

    public void generateLarge3TierTopologyEdge(String jsonFileName, boolean noscale) {
        final int numWeb=4;
        final int numApp=12;
        final int numDB=0;

        final int groupNum = 2;
        final Long[] linkBW = new Long[]{1500000L, 1500000L, 1500000L,
                1500000L, 1500000L, 1500000L, 1500000L, 1500000L, 1500000L, 1500000L, 1500000L, 1500000L,
                1500000L, 1500000L, 1500000L};

        //Random rand = new Random(SEED);
        for(int vmGroupId = 0;vmGroupId < groupNum; vmGroupId++) {
            VirtualTopologyGeneratorVmTypes.TimeGen startTime = new VirtualTopologyGeneratorVmTypes.TimeGen(-1);
            VirtualTopologyGeneratorVmTypes.TimeGen endTime = new VirtualTopologyGeneratorVmTypes.TimeGen(-1);

            generateVMGroupComplex(numWeb, numApp, numDB, startTime, endTime, linkBW[vmGroupId], vmGroupId, noscale);
        }

        wrtieJSON(jsonFileName);
    }

    int vmNum = 0;

    enum VMtype {
        WebServer,
        AppServer,
        DBServer,
        Proxy,
        Firewall
    }

    public VirtualTopologyGenerator.VMSpec createVM(VirtualTopologyGeneratorVmTypesEdge.VMtype vmtype, double startTime, double endTime, int vmGroupId, int vmGroupSubId, long vmBW) {
        String name = "vm";
        int pes = 1;
        long vmSize = 1000;
        long mips=1000;
        int vmRam = 256;
        String datacenter = null;
        String host = null;
        //long vmBW=12000000;

        switch(vmtype) {
            case WebServer:
                //m1.large
//			mips=mips*2;
                mips=100000;//2500;
//			pes=2;
                pes=16;
                vmSize = 100;
                vmRam = 10;
                name="user";
                datacenter = "edgedc";
                host="edgeuser";
                break;
            case AppServer:
                //m2.xlarge
                mips=40000;//(long) (mips*2.5);
                pes=1;
                name="app";
                datacenter = "internet";
                break;
            case DBServer:
                //c1.xlarge
                mips=10000;//(long) (mips*2.5);
                pes=12;
                name="db";
                break;
            case Proxy:
                mips=400;//(long) (mips*2.5);
                pes=4;
                name="proxy";
                break;
            case Firewall:
                mips=400;//(long) (mips*2.5);
                pes=4;
                name="firewall";
                break;
        }
        name += vmGroupId;
        if(host != null)
            host += vmGroupId;

        if(vmGroupSubId != -1) {
            name += "-" + vmGroupSubId;
            if(host != null)
                host += "-" + vmGroupSubId;
        }
        vmNum++;

        VirtualTopologyGenerator.VMSpec vm = addVM(name, datacenter, host, pes, mips, vmRam, vmSize, vmBW, startTime, endTime);
        return vm;
    }

    public void generateVMGroupComplex(int numWeb, int numApp, int numDB, VirtualTopologyGeneratorVmTypes.TimeGen startTime, VirtualTopologyGeneratorVmTypes.TimeGen endTime, Long linkBw, int groupId, boolean noscale) {
        System.out.printf("Generating VM Group(%d)\n", groupId);
        VirtualTopologyGenerator.VMSpec[] webs = new VirtualTopologyGenerator.VMSpec[numWeb];
        VirtualTopologyGenerator.VMSpec[] apps = new VirtualTopologyGenerator.VMSpec[numApp];
        VirtualTopologyGenerator.VMSpec[] dbs = new VirtualTopologyGenerator.VMSpec[numDB];
        for(int i=0;i<numWeb;i++)
            webs[i] = this.createVM(VirtualTopologyGeneratorVmTypesEdge.VMtype.WebServer, startTime.getStartTime(), endTime.getEndTime(), groupId, i, linkBw);

        double sTime = startTime.getStartTime();
        for(int i=0;i<numApp;i++)
        {
            apps[i] = this.createVM(VirtualTopologyGeneratorVmTypesEdge.VMtype.AppServer, sTime, endTime.getEndTime(), groupId, i, linkBw);
            if(i%2 == 1)
                sTime = startTime.getStartTime(); //pair
        }
        for(int i=0;i<numDB;i++)
            dbs[i] = this.createVM(VirtualTopologyGeneratorVmTypesEdge.VMtype.DBServer, startTime.getStartTime(), endTime.getEndTime(), groupId, i, linkBw);

        int maxNum = Integer.max(numWeb, numApp);
        maxNum=Integer.max(maxNum, numDB);

        // add links between VMs
        long linkBwPerCh = linkBw/2;
        if(noscale)
            linkBwPerCh = linkBw;//linkBw;

        if(linkBw > 0) {
            for(int i=0;i<maxNum;i++)
            {
                addLinkAutoNameBoth(webs[i%numWeb], apps[i%numApp], linkBwPerCh);
                //addLinkAutoNameBoth(apps[i%numApp], dbs[i%numDB], linkBwPerCh);
            }
        }

        // Create SFC!!!
        createSFCPolicy(webs, apps, dbs, startTime, endTime, linkBw, groupId, noscale);
    }

    private List<VirtualTopologyGenerator.SFSpec>[] createSFCombination(VirtualTopologyGenerator.SFSpec[] sp1, VirtualTopologyGenerator.SFSpec[] sp2) {
        int maxNum = sp1.length;
        if(sp2 !=null)
            maxNum = Integer.max(sp1.length, sp2.length);

        @SuppressWarnings("unchecked")
        List<VirtualTopologyGenerator.SFSpec>[] chains = new List[maxNum];
        for(int i=0; i<maxNum; i++) {
            chains[i] = new ArrayList<VirtualTopologyGenerator.SFSpec>();
            chains[i].add(sp1[i%sp1.length]);
            if(sp2 !=null)
                chains[i].add(sp2[i%sp2.length]);
        }
        return chains;
    }

    public void createSFCPolicy(VirtualTopologyGenerator.VMSpec[] webs, VirtualTopologyGenerator.VMSpec[] apps, VirtualTopologyGenerator.VMSpec[] dbs, VirtualTopologyGeneratorVmTypes.TimeGen startTime, VirtualTopologyGeneratorVmTypes.TimeGen endTime, Long linkBw, int groupId, boolean noscale) {
        int lb1Num = 1;
        int fwNum = 1;
		/*
		int lb2Num = 1;
		int idsNum = 1;
		*/
        if(noscale) {
            lb1Num = 1;
            fwNum = 3;
			 /*
			 lb2Num = 1;
			 idsNum = 3;
			 */
        }

        VirtualTopologyGenerator.SFSpec[] lb1s = new VirtualTopologyGenerator.SFSpec[lb1Num];
        for(int i=0; i<lb1Num; i++)
        {
            lb1s[i] = addSFLoadBalancer("lb1"+i, linkBw, startTime, endTime, noscale);
        }
        VirtualTopologyGenerator.SFSpec[] fws = new VirtualTopologyGenerator.SFSpec[fwNum];
        for(int i=0; i<fwNum; i++)
        {
            fws[i] = addSFFirewall("fw"+i, linkBw, startTime, endTime, noscale);
        }
		/*
		SFSpec [] lb2s = new SFSpec[lb2Num];
		for(int i=0; i<lb2Num; i++)
		{
			//lb2s[i] = addSFLoadBalancer("lb2"+i, linkBw, startTime, endTime, noscale);
		}
		SFSpec [] idss = new SFSpec[idsNum];
		for(int i=0; i<idsNum; i++)
		{
			//idss[i] = addSFIntrusionDetectionSystem("ids"+i, linkBw, startTime, endTime, noscale);
		}
		*/

        // Policy for Web -> App
        {
            //List<SFSpec>[] chains = createSFCombination(fws, lb1s);
            List<VirtualTopologyGenerator.SFSpec>[] chains = createSFCombination(fws, null);
            double expTime = 1.0;
            if(groupId == 1)
                expTime = 2.0;
            addSFCPolicyCollective(webs, apps, chains, expTime);
        }

        {
            List<VirtualTopologyGenerator.SFSpec>[] chains = createSFCombination(lb1s, null);
            double expTime = 1.0;
            if(groupId == 1)
                expTime = 2.0;
            addSFCPolicyCollective(apps, webs, chains, expTime);
        }

        // Policy for App -> DB
		/*
		{
			List<SFSpec>[] chains = createSFCombination(lb2s, idss);
			double expTime = 1.0;
			addSFCPolicyCollective(apps, dbs, chains, expTime);
		}//*/

        // Policy for DB -> App
		/*
		{
			List<SFSpec>[] chains = createSFCombination(idss, lb2s);
			double expTime = 1.0;
			addSFCPolicyCollective(dbs, apps, chains, expTime);
		}//*/

        // Policy for App -> Web
		/*
		{
			List<SFSpec> chain = new ArrayList<SFSpec>();
			chain.add(lb1s[0]);
			@SuppressWarnings("unchecked")
			List<SFSpec>[] chains = new List[1];
			chains[0] = chain;
			double expTime = 1.0;
			addSFCPolicyCollective(apps, webs, chains, expTime);
		}//*/
    }


    public void addSFCPolicyCollective(VirtualTopologyGenerator.VMSpec[] srcList, VirtualTopologyGenerator.VMSpec[] dstList, List<VirtualTopologyGenerator.SFSpec>[] sfChains, double expectedTime) {
        int maxNum = Integer.max(srcList.length, dstList.length);
        for(int i=0;i<maxNum;i++)
        {
            VirtualTopologyGenerator.VMSpec src = srcList[i%srcList.length];
            VirtualTopologyGenerator.VMSpec dest = dstList[i%dstList.length];
            List<VirtualTopologyGenerator.SFSpec> sfChain = sfChains[i%sfChains.length];
            String linkname = getAutoLinkName(src, dest);
            String policyname = "sfc-"+linkname;

            //double thisExp = expectedTime;
            //if(src.name.charAt(src.name.length()-1) - '0' < srcList.length/2)
            //	thisExp *= 2;
            addSFCPolicy(policyname, src, dest, linkname, sfChain, expectedTime);
        }
    }

    public VirtualTopologyGenerator.SFSpec addSFFirewall(String name, long linkBw, VirtualTopologyGeneratorVmTypes.TimeGen startTime, VirtualTopologyGeneratorVmTypes.TimeGen endTime, boolean noscale) {
        String datacenter = "edgedc";
        String [] subdatacenter = {"netcloud"};

        int pes = 8; // for AutoScale
        if(noscale)
            pes = 16; 	// for fixed number : total mips = 3*8000 = 24,000. MI/op = 25. -> 960 operations / sec
        long mips = 10000;
        int ram = 1000;
        long storage = 1000;
        long bw = linkBw;
        //long miPerOperation = 25;
        long miPerOperation = 800;
        VirtualTopologyGenerator.SFSpec sf = addSF(name, datacenter, Arrays.asList(subdatacenter), pes, mips, ram, storage, bw, startTime.getStartTime(), endTime.getEndTime(), miPerOperation, "Firewall");

        return sf;
    }

    public VirtualTopologyGenerator.SFSpec addSFLoadBalancer(String name, long linkBw, VirtualTopologyGeneratorVmTypes.TimeGen startTime, VirtualTopologyGeneratorVmTypes.TimeGen endTime, boolean noscale) {
        String datacenter = "edgedc";
        String [] subdatacenter = {"netcloud"};
        int pes = 2; // for AutoScale
        if(noscale)
            pes = 10;	// for fixed number : total mips = 5*8000 = 40,000. MI/op = 10. -> 4,000 operations / sec
        long mips = 10000;
        int ram = 1000;
        long storage = 1000;
        long bw = linkBw;
        long miPerOperation = 20; //10
        VirtualTopologyGenerator.SFSpec sf = addSF(name, datacenter, Arrays.asList(subdatacenter), pes, mips, ram, storage, bw, startTime.getStartTime(), endTime.getEndTime(), miPerOperation, "LoadBalancer");

        return sf;
    }
}
