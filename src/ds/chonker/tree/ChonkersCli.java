package ds.chonker.tree;
import java.nio.file.*;
import java.io.*;
import java.util.*;

import ds.chonker.tree.ChonkersMonoidData.Minimal;

public class ChonkersCli {
  public static void main(String[] args) throws Exception {
	  if(args.length==0) {
		  
		  args = new String[] {
			"--input", "/home/bb/test/test.in",
			"--target", "1024",
			"--out", "/home/bb/test/test.out",
		  };
	  }
    Map<String,String> arg = parseArgs(args);
    Path input = Paths.get(arg.get("--input"));
    int target  = Integer.parseInt(arg.get("--target"));
    Path outDir = Paths.get(arg.get("--out"));
    
    System.out.println("Output file is: "+outDir);

    byte[] data = Files.readAllBytes(input); // or stream in chunks if you prefer
    // ---- Run your existing Chonkers partitioner here ----
    // Result should be a list of boundaries (including 0 and data.length)
    List<Integer> cuts = runChonkers(data, target);

    // Write cuts.txt
    try (BufferedWriter w = Files.newBufferedWriter(outDir)) {
      for (int c : cuts) { w.write(Integer.toString(c)); w.newLine(); }
    }
  }

  static Map<String,String> parseArgs(String[] a) {
    Map<String,String> m = new HashMap<>();
    for (int i=0;i<a.length;i+=2) m.put(a[i], a[i+1]);
    return m;
  }

  static List<Integer> runChonkers(byte[] data, int target) {
	  List<Integer> result = new ArrayList<>();
	  if(data.length==0)
		  return result;
	  ChonkerConfig<ChonkersMonoidData.Minimal> config = ChonkerConfig.bytesWithTarget(target);
	  @SuppressWarnings("unchecked")
	  ChonkerNode<ChonkersMonoidData.Minimal> [] table = new ChonkerNode[256];
	  for(int i=0; i<table.length; ++i) {
		  table[i] = config.canonical(new ChonkerLeaf.ByteLeaf(i));
	  }
	  List<ChonkerNode<ChonkersMonoidData.Minimal>> leaves = new ArrayList<>();
	  for(byte b : data) {
		  leaves.add(table[0xFF & ((int)b)]);
	  }
	  int targetLayer = Integer.bitCount(Integer.highestOneBit(target-1)-1)+1;
	  ChonkerNode<Minimal> root = new Rechonker<>(config, ChonkerTreeZipper.end(), leaves, ChonkerTreeZipper.end())
			  .run();
	  int tag = ChonkerNode.encodeLevelTag(targetLayer, 2, 5);

	  ChonkerTreeZipper<Minimal> at = new ChonkerTreeZipper<>(root).leftMost(tag);
	  int atI = 0;
	  while(!at.isEnd()) {
          atI += at.node.weight()/8;
          at = at.right(tag);
          result.add(atI);
      }
      return result;
  }
	
}
