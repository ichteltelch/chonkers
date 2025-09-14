package ds.chonker.tree;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;

import ds.chonker.tree.ChonkerLeaf.CharLeaf;
import ds.chonker.tree.ChonkersMonoidData.WithUserMonoids;

public class Yarn implements CharSequence, Comparable<Yarn>{

	public static UserMonoid<ChonkerNode<ChonkersMonoidData.WithUserMonoids>> REVERSE = new UserMonoid<ChonkerNode<WithUserMonoids>>() {

		@Override
		public ChonkerNode<WithUserMonoids> power(WithUserMonoids base, long exponent) {
			return repeat(base.content().getMonoidData().get(this), exponent);
		}


		@Override
		public ChonkerNode<WithUserMonoids> inject(ChonkerLeaf<WithUserMonoids> leaf) {
			return leaf;
		}

		@Override
		public ChonkerNode<WithUserMonoids> combine(WithUserMonoids left, WithUserMonoids right) {
			ChonkerNode<WithUserMonoids> leftReverse = left.content().getMonoidData().get(this);
			ChonkerNode<WithUserMonoids> rightReverse = right.content().getMonoidData().get(this);
			return cc.computeConcat(rightReverse, leftReverse);
		}
	};


	private static final Yarn EMPTY = new Yarn(null);
	public static ChonkerConfig<ChonkersMonoidData.WithUserMonoids> cc = ChonkerConfig.CHARS;
	final ChonkerNode<ChonkersMonoidData.WithUserMonoids> heComin;
	private Yarn(ChonkerNode<ChonkersMonoidData.WithUserMonoids> heComin) {
		this.heComin = heComin;
	}
	protected static ChonkerNode<WithUserMonoids> repeat(ChonkerNode<WithUserMonoids> chonkerNode, long exponent) {
		return cc.computeRepeats(chonkerNode, exponent);
	}
	public static Yarn of(CharSequence s) {
		if(s instanceof Yarn)
			return (Yarn) s;
		if(s.length()==0)
			return EMPTY;
		else if(s.length()==1) {
			return new Yarn(cc.canonical(new ChonkerLeaf.CharLeaf(s.charAt(0))));
		}
		@SuppressWarnings("unchecked")
		ChonkerLeaf<ChonkersMonoidData.WithUserMonoids>[] seq = new ChonkerLeaf[s.length()];
		for (int i = 0; i < s.length(); i++) {
			seq[i] = (ChonkerLeaf<ChonkersMonoidData.WithUserMonoids>) cc.canonical(new ChonkerLeaf.CharLeaf(s.charAt(i)));
		}
		ChonkerNode<ChonkersMonoidData.WithUserMonoids> heComin = new Rechonker<>(cc, ChonkerTreeZipper.end(), Arrays.asList(seq), ChonkerTreeZipper.end())
				.run();
		return new Yarn(heComin);
	}
	public Yarn concat(Yarn other) {
		if(heComin==null)
			return other;
		if(other.heComin==null)
			return this;
		if(longLength() + other.longLength()>(1L<<57))
			throw new IllegalArgumentException("Too long");
		return new Yarn(cc.computeConcat(heComin, other.heComin));
	}
	@Override
	public String toString() {
		if(heComin==null)
			return "";
		StringBuilder sb = new StringBuilder();
		giveString(heComin, sb);
		return sb.toString();
	}
	public StringBuilder giveString(StringBuilder sb) {
		if(heComin!=null)
			giveString(heComin, sb);
		return sb;
	}
	public PrintStream print(PrintStream stream) {
		if(heComin!=null)
			print(heComin, stream);
		return stream;
	}
	public PrintWriter print(PrintWriter writer) {
		if(heComin!=null)
			print(heComin, writer);
		return writer;
	}
	static void giveString(ChonkerNode<?> node, StringBuilder sb) {
		if(node instanceof CharLeaf) {
			sb.append(((CharLeaf) node).value);
		} else {
			for(long childIndex = 0; childIndex < node.numChildren(); ++childIndex) {
				giveString(node.getChild(childIndex), sb);
			}
		}
	}
	static void print(ChonkerNode<?> node, PrintStream stream) {
		if(node instanceof CharLeaf) {
			stream.print(((CharLeaf) node).value);
		} else {
			for(long childIndex = 0; childIndex < node.numChildren(); ++childIndex) {
				print(node.getChild(childIndex), stream);
			}
		}
	}
	static void print(ChonkerNode<?> node, PrintWriter writer) {
		if(node instanceof CharLeaf) {
			writer.print(((CharLeaf) node).value);
		} else {
			for(long childIndex = 0; childIndex < node.numChildren(); ++childIndex) {
				print(node.getChild(childIndex), writer);
			}
		}
	}
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null)
			return false;
		if (o instanceof Yarn) {
			Yarn a = (Yarn) o;
			if(heComin == null)
				return a.heComin == null;
			if(a.heComin==null)
				return false;
			return heComin.equalContent(a.heComin);
		}
		return false;
	}
	@Override
	public int hashCode() {
		return heComin.augHash();
	}
	@Override
	public int length() {
		if(heComin==null)
			return 0;
		long len = (heComin.weight()>>>5);
		if(len>Integer.MAX_VALUE)
			throw new ArithmeticException("This yarn is too large for its length to be represented as an int");
		return (int)len;
	}
	@Override
	public int compareTo(Yarn o) {
		if(heComin==null) {
			if(o.heComin==null) {
				return 0;
			}
			return -1;
		}
		if(o.heComin==null) {
			return 1;
		}
		long diffbit = heComin.getRawDiffbit_leafReverse(0, 0, o.heComin);
		if(diffbit<0)
			return Long.compare(heComin.weight(), o.heComin.weight());
		return 1-2*(int)(diffbit&1);
		
	}
	public long longLength() {
		if(heComin==null)
			return 0;
		long len = (heComin.weight()>>>5);
		return len;
	}
	@Override
	public char charAt(int index) {
		return charAt((long)index);
	}


	public char charAt(long index) {
		if(heComin==null)
			throw new IndexOutOfBoundsException("This yarn is empty");
		if(index<0 || index>=heComin.weight()>>>5)
			throw new IndexOutOfBoundsException("Index: "+index+", Size: "+(heComin.weight()>>5));
		long target = index<<5;
		ChonkerNode<?> at = heComin;
		while(target>0) {
			ChonkerNode<?> c0 = at.getChild(0);
			long w0 = c0.weight();
			if(target < w0) {
				at = c0;
				continue;
			} else {
				target -= w0;
			}
			ChonkerNode<?> c1 = at.getChild(1);
			if(target < c1.weight()) {
				at = c1;
				continue;
			}
			assert at instanceof Caterpillar;
			target = target % w0;
			at = c0;
		}
		while(!(at instanceof ChonkerLeaf)) {
			ChonkerNode<?> w03 = at.getChild(0);
			at = w03;
		}
		return ((CharLeaf) at).value;
	}
	@Override
	public Yarn subSequence(int start, int end) {
		return subSequence((long)start, (long)end);
	}
	public Yarn subSequence(long start, long end) {
		if(start<0)
			throw new IndexOutOfBoundsException("Start index: "+start);
		if(end>longLength())
			throw new IndexOutOfBoundsException("End index: "+end+", Size: "+longLength());
		if(start==end)
			return EMPTY;
		if(start==0)
			return prefix(end);
		if(end==longLength())
			return suffix(start);
		return prefix(end).suffix(start);

	}
	public Yarn prefix(long end) {
		if(end<=0) {
			if(end==0)
				return EMPTY;
			throw new IndexOutOfBoundsException("End index: "+end);
		}
		if(end>=longLength()) {
			if(end==longLength())
				return this;
			throw new IndexOutOfBoundsException("End index: "+end+", Size: "+longLength());
		}
		return new Yarn(new Rechonker<>(cc, 
				new ChonkerTreeZipper<>(heComin).zipTo((end-1)<<5), 
				Collections.emptyList(), 
				ChonkerTreeZipper.end())
				.run());
	}
	public Yarn suffix(long start) {
		if(start<=0) {
			if(start==0)
				return this;
			throw new IndexOutOfBoundsException("Start index: "+start);
		}
		if(start>=longLength()) {
			if(start==longLength())
				return EMPTY;
			throw new IndexOutOfBoundsException("Start index: "+start+", Size: "+longLength());
		}
		return new Yarn(new Rechonker<>(cc, 
				ChonkerTreeZipper.end(), 
				Collections.emptyList(), 
				new ChonkerTreeZipper<>(heComin).zipTo(start<<5)).run());

	}
	public Yarn dropSuffix(long dropCount) {
		return prefix(longLength() - dropCount);
	}
	public Yarn dropPrefix(long dropCount) {
		return suffix(longLength() - dropCount);
	}
	public Yarn replaceSubstring(int start, int end, CharSequence replacement) {
		return prefix(start).concat(Yarn.of(replacement)).concat(suffix(end));
	}
	public long commonPrefixLength(Yarn other) {
		return commonPrefixLength(0, other, 0);
	}
	public long commonPrefixLength(long start, Yarn other, long otherStart) {
		if(heComin==null || other.heComin==null)
			return 0;
		long diffbit = heComin.getRawDiffbit(start<<5, otherStart<<5, other.heComin);
		if(diffbit<0)
			return Math.min(longLength() - start, other.length()-start);
		return diffbit >>> 6;
	}
	public long commonSuffixLength(Yarn other) {
		return commonSuffixLength(longLength(), other, other.longLength());
	}
	public long commonSuffixLength(long end, Yarn other, long otherEnd) {
		if(heComin==null || other.heComin==null)
			return 0;
		long diffbit = heComin.getReverseDiffbit(
				(longLength()-end)<<5, 
				(other.longLength()-otherEnd)<<5, 
				other.heComin);
		if(diffbit<0)
			return Math.min(end, otherEnd);
		return diffbit >>> 6;
	}

	public int getStructureNodeCount() {
		return heComin==null?0:heComin.getStructureNodeCount();
	}
	public int getContentNodeCount() {
		return heComin==null?0:heComin.getContentNodeCount();
	}
	public int getSharedStructureNodeCount(Yarn other) {
		if(heComin==null)
			return 0;
		if(other.heComin==null)
			return 0;
		return heComin.getSharedContentNodeCount(other.heComin);
	}
	public int getSharedContentNodeCount(Yarn other) {
		if(heComin==null)
			return 0;
		if(other.heComin==null)
			return 0;
		return heComin.getSharedContentNodeCount(other.heComin);
	}
	public Yarn reverse() {
		if(heComin==null)
			return this;
		return new Yarn(heComin.getMonoidData().get(REVERSE));
	}
	public Yarn repeat(long count) {
		if(count==0 || heComin==null)
			return EMPTY;
		if(count==1)
			return this;
		if(count<0)
			return reverse().repeat(-count);
		return new Yarn(cc.computeRepeats(heComin, count));
	}
	public static void main(String[] args) throws InterruptedException {
		{
			{
				Yarn a = Yarn.of("Hello, ");
				Yarn b = Yarn.of("World! ");
				Yarn ab = a.concat(b);
				System.out.println("^"+ab+"$");
				System.out.println("^"+ab.prefix(0)+"$");
				System.out.println("^"+ab.prefix(1)+"$");
				System.out.println("^"+ab.prefix(2)+"$");
				System.out.println("^"+ab.suffix(ab.length())+"$");
				System.out.println("^"+ab.suffix(ab.length()-1)+"$");
				System.out.println("^"+ab.suffix(ab.length()-2)+"$");
				Yarn l = ab;
				l = l.concat(l);
				l = l.concat(l);
				l = l.concat(l);
				l = l.concat(l);
				l = l.concat(l);
				l = l.concat(l);

				System.out.println(l);
				Yarn ls = l.replaceSubstring(30, 50, "Fsfdsf");
				System.out.println(ls);
				System.out.println(l.reverse());
				System.out.println(ls.reverse());
			}
			Yarn fib0 = Yarn.of("b");
			Yarn fib1 = Yarn.of("a");
			{
				long _t0 = System.currentTimeMillis();

				for(int i=0; i<80; ++i) {
					long t0 = System.currentTimeMillis();
					Yarn fibn = fib1.concat(fib0);
					long t1 = System.currentTimeMillis();
					fib0 = fib1;
					fib1 = fibn;
					//			for(int j=0; j<10; ++j) {
					//				for(int k=0; k<3; ++k)
					//					System.gc();
					//				Thread.sleep(10);
					//			}
					System.out.println(i + " " + fib1.longLength() +" "+ (6 + Long.bitCount(Long.highestOneBit(fib1.longLength() - 1)-1)));
					//			System.out.println(cc.canon.size());
					System.out.println(fib0.getStructureNodeCount() + " _ "+fib0.getContentNodeCount());
					System.out.println(fib1.getStructureNodeCount() + " _ "+fib1.getContentNodeCount());
					System.out.println(fib1.getSharedStructureNodeCount(fib0) + " _ "+fib1.getSharedContentNodeCount(fib0));
					System.out.println((t1-t0) +" ms");

				}
				long _t1 = System.currentTimeMillis();
				System.out.println((_t1-_t0) +" ms");
			}

			//		fib1.print(System.out).println();
			System.out.println(fib1.longLength());
			System.out.println(fib1.subSequence(fib1.longLength()/2, fib1.longLength()/2 + 100));

			//fib1.reverse();
			long t0 = System.currentTimeMillis();
			Yarn palindrome = fib1.dropSuffix(2);
			System.out.println(palindrome.getContentNodeCount());

			Yarn reversePalindrome = palindrome.reverse();
			reversePalindrome.reverse();
			System.out.println(palindrome.equals(reversePalindrome));
			long t1 = System.currentTimeMillis();
			System.out.println("Reverse Fibonacci word: "+(t1-t0) +" ms");


//			char[] chars = new char[1000000];
//			for(int i=0; i<chars.length; ++i)
//				chars[i] = (char)('A' + (int)(Math.random()*26));
//			String str = new String(chars);


//			t0 = System.currentTimeMillis();
//			Yarn randomYarn = Yarn.of(str);
//			t1 = System.currentTimeMillis();
//			System.out.println("Make random Yarn: "+(t1-t0) +" ms");
//			randomYarn.reverse();
//			long t2= System.currentTimeMillis();
//			System.out.println("Reverse random Yarn:"+(t2-t1) +" ms");
		}
		System.gc();
		System.out.println(cc.canon.size());
		System.out.println(cc.monoidCanon.size());
		
		testCompare("ab", "c");
		testCompare("ab", "cd");
		testCompare("ab", "cde");
		testCompare("ab", "b");
		testCompare("ab", "bc");
		testCompare("ab", "bcd");
		testCompare("ab", "a");
		testCompare("ab", "ab");
		testCompare("ab", "abc");
		testCompare("a", "a");
		testCompare("", "a");
	}
	private static void testCompare(String a, String b) {
		System.out.println(a.compareTo(b) + " == " + Yarn.of(a).compareTo(Yarn.of(b)));
		System.out.println(b.compareTo(a) + " == " + Yarn.of(b).compareTo(Yarn.of(a)));
	}
	

}
