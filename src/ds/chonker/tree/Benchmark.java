package ds.chonker.tree;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import ds.chonker.tree.ChonkerLeaf.CharLeaf;
import ds.chonker.tree.ChonkersMonoidData.WithUserMonoids;

public class Benchmark {
	public static final String TABLE = "tabular";
	static class Gaussian{
		double mean;
		double stdDev;
		double min;
		double max;
		public Gaussian(double mean, double stdDev, double min, double max){
			this.mean = mean;
			this.stdDev = stdDev;
			this.min = min;
			this.max = max;
		}
		public <T> Gaussian(T[] data, ToDoubleFunction<? super T> extract, Predicate<? super T> filter){
			double sum = 0;
			int count = 0;
			min = Double.POSITIVE_INFINITY;
			max = Double.NEGATIVE_INFINITY;
			for(T inst: data) {
				if(!filter.test(inst))
					continue;
				double value = extract.applyAsDouble(inst);
				if(Double.isNaN(value))
					continue;
				sum += value;
				min = Math.min(min, value);
				max = Math.max(max, value);
				++count;
			}
			mean = sum / count;
			double variance = 0;
			for(T inst: data) {
				if(!filter.test(inst))
					continue;
				double value = extract.applyAsDouble(inst);
				if(Double.isNaN(value))
					continue;
				variance += Math.pow(value - mean, 2);
			}
			stdDev = Math.sqrt(variance / count);
		}
		public <T> Gaussian(Iterable<? extends T> data, ToDoubleFunction<? super T> extract, Predicate<? super T> filter){
			double sum = 0;
			int count = 0;
			min = Double.POSITIVE_INFINITY;
			max = Double.NEGATIVE_INFINITY;
			for(T inst: data) {
				if(!filter.test(inst))
					continue;
				double value = extract.applyAsDouble(inst);
				if(Double.isNaN(value))
					continue;
				sum += value;
				min = Math.min(min, value);
				max = Math.max(max, value);
				++count;
			}
			mean = sum / count;
			double variance = 0;
			for(T inst: data) {
				if(!filter.test(inst))
					continue;
				double value = extract.applyAsDouble(inst);
				if(Double.isNaN(value))
					continue;
				variance += Math.pow(value - mean, 2);
			}
			stdDev = Math.sqrt(variance / count);
		}
		public String toString() {
			return "Gaussian(mean="+mean+", stdDev="+stdDev+
					", min="+min+
					", max="+max+")";
		}
		public String toString(double factor) {
			return "Gaussian(mean="+mean*factor+
					", stdDev="+stdDev*factor+
					", min="+min*factor+
					", max="+max*factor+")";
		}
		public String toTeX(double factor) {
			return String.format("%.2f \\pm %.2f \\in [%.2f; %.2f]", 
					mean*factor,
					stdDev*factor,
					min*factor,
					max*factor,
					null);
		}
		public String toTeX_noMin(double factor) {
			return String.format("%.3f \\pm %.3f \\leq %.4f", 
					mean*factor,
					stdDev*factor,
					max*factor,
					null);
		}
		public String toTeX_noBounds(double factor) {
			return String.format("%.3f \\pm %.3f", 
					mean*factor,
					stdDev*factor,
					max*factor,
					null);
		}
	}
	static class GaussianSizeResult{
		int levels;
		Gaussian[] averageWeightPerLevel;
		Gaussian[] stdDevWeightPerLevel;
		Gaussian[] minWeightPerLevel;
		Gaussian[] maxWeightPerLevel;
		Gaussian[] maxSegmentWeightPerLevel;
		Gaussian[] minPairWeightPerLevel;
		public String toTeX(boolean full) {
			StringBuilder sb = new StringBuilder();
			if(full) {
				sb.append("\\begin{"+TABLE+"}{r|r|r|r|r|r|r|}\n");
				sb.append("layer & average & $\\sigma$ & min & max & max segment & min pair \\\\");
			}else {
				sb.append("\\begin{"+TABLE+"}{r|r|r|r|r|}\n");
				sb.append("layer & average & $\\sigma$ & max semgent & min pair \\\\");
			}
			sb.append("\\hline\n");
			for(int level = 1; level<levels; ++level) {
				sb.append("$").append(level);
				double factor = 1d / ChonkerConfig.CHARS.absoluteUnit(level);
				sb.append("$ & $").append(averageWeightPerLevel[level].toTeX(factor));
				if(full) {
					sb.append("$ & $").append(stdDevWeightPerLevel[level].toTeX(factor));
					sb.append("$ & $").append(minWeightPerLevel[level].toTeX(factor));
					sb.append("$ & $").append(maxWeightPerLevel[level].toTeX(factor));
				}else {
					sb.append("$ & $").append(stdDevWeightPerLevel[level].toTeX_noBounds(factor));
					//					sb.append("$ & $").append(String.format("%.3f", stdDevWeightPerLevel[level].toTeX(factor)));	
				}
				sb.append("$ & $").append(maxSegmentWeightPerLevel[level].toTeX(factor));
				sb.append("$ & $").append(minPairWeightPerLevel[level].toTeX(factor));
				sb.append("$ \\\\\n");
			}
			sb.append("\\hline\n");
			sb.append("\\end{"+TABLE+"}");
			return sb.toString();
		}
		public GaussianSizeResult(SingleSizeResult[] singles) {
			for(SingleSizeResult single: singles) {
				levels = Math.max(levels, single.levels);
			}
			averageWeightPerLevel = new Gaussian[levels];
			stdDevWeightPerLevel = new Gaussian[levels];
			minWeightPerLevel = new Gaussian[levels];
			maxWeightPerLevel = new Gaussian[levels];
			maxSegmentWeightPerLevel = new Gaussian[levels];
			minPairWeightPerLevel = new Gaussian[levels];
			for(int level = 0; level<levels; ++level) {
				final int fLevel = level;
				Predicate<SingleSizeResult> filter = single -> single.levels>fLevel;
				averageWeightPerLevel[level] = new Gaussian(singles, s->s.averageWeightPerLevel[fLevel], filter);
				stdDevWeightPerLevel[level] = new Gaussian(singles, s->s.stdDevWeightPerLevel[fLevel], filter);
				minWeightPerLevel[level] = new Gaussian(singles, s->s.minWeightPerLevel[fLevel], filter);
				maxWeightPerLevel[level] = new Gaussian(singles, s->s.maxWeightPerLevel[fLevel], filter);
				maxSegmentWeightPerLevel[level] = new Gaussian(singles, s->s.maxSegmentWeightPerLevel[fLevel], filter);
				minPairWeightPerLevel[level] = new Gaussian(singles, s->s.minPairWeightPerLevel[fLevel], filter);
			}

		}
		public GaussianSizeResult(Iterable<? extends SingleSizeResult> singles) {
			for(SingleSizeResult single: singles) {
				levels = Math.max(levels, single.levels);
			}
			averageWeightPerLevel = new Gaussian[levels];
			stdDevWeightPerLevel = new Gaussian[levels];
			minWeightPerLevel = new Gaussian[levels];
			maxWeightPerLevel = new Gaussian[levels];
			maxSegmentWeightPerLevel = new Gaussian[levels];
			minPairWeightPerLevel = new Gaussian[levels];
			for(int level = 0; level<levels; ++level) {
				final int fLevel = level;
				Predicate<SingleSizeResult> filter = single -> single.levels>fLevel;
				averageWeightPerLevel[level] = new Gaussian(singles, s->s.averageWeightPerLevel[fLevel], filter);
				stdDevWeightPerLevel[level] = new Gaussian(singles, s->s.stdDevWeightPerLevel[fLevel], filter);
				minWeightPerLevel[level] = new Gaussian(singles, s->s.minWeightPerLevel[fLevel], filter);
				maxWeightPerLevel[level] = new Gaussian(singles, s->s.maxWeightPerLevel[fLevel], filter);
				maxSegmentWeightPerLevel[level] = new Gaussian(singles, s->s.maxSegmentWeightPerLevel[fLevel], filter);
				minPairWeightPerLevel[level] = new Gaussian(singles, s->s.minPairWeightPerLevel[fLevel], filter);
			}

		}
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("GaussianSizeResult{levels="+levels+"}");
			for(int level = 0; level<levels; ++level) {
				sb.append("\n  Level ").append(level).append(": ");
				double factor = 1d / ChonkerConfig.CHARS.absoluteUnit(level);
				sb.append("\n    avg: ").append(averageWeightPerLevel[level].toString(factor));
				sb.append("\n    stdDev: ").append(stdDevWeightPerLevel[level].toString(factor));
				sb.append("\n    min: ").append(minWeightPerLevel[level].toString(factor));
				sb.append("\n    max: ").append(maxWeightPerLevel[level].toString(factor));
				sb.append("\n    maxSeg: ").append(maxSegmentWeightPerLevel[level].toString(factor));
				sb.append("\n    minPair: ").append(minPairWeightPerLevel[level].toString(factor));
			}
			return sb.toString();
		}

	}
	static class GaussianLocalityResult{
		int levels;
		Gaussian[] differentChunksLeftOrig;
		Gaussian[] differentChunksRightOrig;
		Gaussian[] differentChunksLeftModi;
		Gaussian[] differentChunksRightModi;
		Gaussian[] differentBitsLeft;
		Gaussian[] differentBitsRight;
		public String toTeX(boolean full) {
			StringBuilder sb = new StringBuilder();
			if(full)
				sb.append("\\begin{"+TABLE+"}{r|r|r|r|r|r|r|}\n");
			else
				sb.append("\\begin{"+TABLE+"}{r|r|r|}\n");

			sb.append("layer");
			if(full)
				sb.append(" & chunks left orig & chunks right orig & chunks left modified & chunks right modified");
			sb.append(" & left & right \\\\\n");
			sb.append("\\hline\n");
			for(int level = 1; level<levels; ++level) {
				sb.append("$").append(level);
				double factor = 1d / ChonkerConfig.CHARS.absoluteUnit(level);
				if(full) {
					sb.append("$ & $").append(differentChunksLeftOrig[level].toTeX(factor));
					sb.append("$ & $").append(differentChunksRightOrig[level].toTeX(factor));
					sb.append("$ & $").append(differentChunksLeftModi[level].toTeX(factor));
					sb.append("$ & $").append(differentChunksRightModi[level].toTeX(factor));
				}
				sb.append("$ & $").append(differentBitsLeft[level].toTeX_noMin(factor));
				sb.append("$ & $").append(differentBitsRight[level].toTeX_noMin(factor));
				sb.append("$ \\\\\n");
			}
			sb.append("\\hline\n");
			sb.append("\\end{"+TABLE+"}");
			return sb.toString();
		}
		public GaussianLocalityResult(SingleLocalityResult[] singles) {
			for(SingleLocalityResult single: singles) {
				levels = Math.max(levels, single.levels);
			}
			differentChunksLeftOrig = new Gaussian[levels];
			differentChunksRightOrig = new Gaussian[levels];
			differentChunksLeftModi = new Gaussian[levels];
			differentChunksRightModi = new Gaussian[levels];
			differentBitsLeft = new Gaussian[levels];
			differentBitsRight = new Gaussian[levels];
			for(int level = 0; level<levels; ++level) {
				final int fLevel = level;
				Predicate<SingleLocalityResult> filter = single -> single.levels>fLevel;
				differentChunksLeftOrig[level] = new Gaussian(singles, s->s.differentChunksLeftOrig[fLevel], filter);
				differentChunksRightOrig[level] = new Gaussian(singles, s->s.differentChunksRightOrig[fLevel], filter);
				differentChunksLeftModi[level] = new Gaussian(singles, s->s.differentChunksLeftModi[fLevel], filter);
				differentChunksRightModi[level] = new Gaussian(singles, s->s.differentChunksRightModi[fLevel], filter);
				differentBitsLeft[level] = new Gaussian(singles, s->s.differentBitsLeft[fLevel], filter);
				differentBitsRight[level] = new Gaussian(singles, s->s.differentBitsRight[fLevel], filter);
			}
		}
		public GaussianLocalityResult(Iterable<? extends SingleLocalityResult> singles) {
			for(SingleLocalityResult single: singles) {
				levels = Math.max(levels, single.levels);
			}
			differentChunksLeftOrig = new Gaussian[levels];
			differentChunksRightOrig = new Gaussian[levels];
			differentChunksLeftModi = new Gaussian[levels];
			differentChunksRightModi = new Gaussian[levels];
			differentBitsLeft = new Gaussian[levels];
			differentBitsRight = new Gaussian[levels];
			for(int level = 0; level<levels; ++level) {
				final int fLevel = level;
				Predicate<SingleLocalityResult> filter = single -> single.levels>fLevel;
				differentChunksLeftOrig[level] = new Gaussian(singles, s->s.differentChunksLeftOrig[fLevel], filter);
				differentChunksRightOrig[level] = new Gaussian(singles, s->s.differentChunksRightOrig[fLevel], filter);
				differentChunksLeftModi[level] = new Gaussian(singles, s->s.differentChunksLeftModi[fLevel], filter);
				differentChunksRightModi[level] = new Gaussian(singles, s->s.differentChunksRightModi[fLevel], filter);
				differentBitsLeft[level] = new Gaussian(singles, s->s.differentBitsLeft[fLevel], filter);
				differentBitsRight[level] = new Gaussian(singles, s->s.differentBitsRight[fLevel], filter);
			}
		}
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("GaussianLocalityResult{levels="+levels+"}");
			for(int level = 0; level<levels; ++level) {
				sb.append("\n  Level ").append(level).append(": ");
				double factor = 1d / ChonkerConfig.CHARS.absoluteUnit(level);
				sb.append("\n    chunksLeftOrig: ").append(differentChunksLeftOrig[level]);
				sb.append("\n    chunksRightOrig: ").append(differentChunksRightOrig[level]);
				sb.append("\n    chunksLeftModi: ").append(differentChunksLeftModi[level]);
				sb.append("\n    chunksRightModi: ").append(differentChunksRightModi[level]);
				sb.append("\n    weightLeft: ").append(differentBitsLeft[level].toString(factor));
				sb.append("\n    WeightRight: ").append(differentBitsRight[level].toString(factor));
			}
			return sb.toString();
		}

	}
	static class PhaseCensus{
		long[] counts;
		int maxTag = 0;
		long total;
		long byPhase[];
		public PhaseCensus(Corpus<?> corpus) {
			//double maxLevel = corpus.data.parallelStream().max((a, b)->Integer.compare(a.heComin.layerTag(), b.heComin.layerTag())).get().heComin.layerTag();
			counts = new long[1000];
			AtomicInteger i = new AtomicInteger(0);
			AtomicLong s = new AtomicLong(0);
			for(CharSequence str: corpus.data) {
				Yarn yarn = Yarn.of(str);
				int tag = yarn.heComin.layerTag();
				maxTag = Math.max(maxTag, tag);
				if(tag>=counts.length)
					counts = Arrays.copyOf(counts, tag*2);

				long ss = s.addAndGet(yarn.longLength());
				int ii = i.getAndIncrement();
				if(ii%100==0) {
					System.out.println("Phase census: Processed "+ii+" strings. Total weight: "+ss);
				}
				process(counts, yarn.heComin);
			}
			byPhase = new long[16];
			for(int a=0; a<counts.length; ++a) {
				long c = counts[a];
				total += c;
				byPhase[a&0xF] += c;

			}
		}
		private void process(long[] counts, ChonkerNode<WithUserMonoids> node) {
			if(node.level()==0)
				return;
			counts[node.layerTag()] += 1;
			for(long i=0; i<node.numChildren(); ++i)
				process(counts, node.getChild(i));

		}
		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder();
			ret.append("PhaseCensus:\n");
			giveString(byPhase, 0, ret);
			return ret.toString();
		}
		public String toTeX() {
			StringBuilder sb = new StringBuilder();
			sb.append("\\begin{"+TABLE+"}{r|r|r|r|r|r|r|r|r|r|}\n");
			sb.append("layer & \\multicolumn{2}{|c|}{Balancing phase} & Caterpillar & \\multicolumn{6}{|c|}{Diffbit phase} \\\\\n");
			sb.append(" & $0$ & $1$ & \\multicolumn{1}{|c|}{phase} & $0$ & $1$ & $2$ & $3$ & $4$& $5$  \\\\\n");
			sb.append("\\hline\n");
			sb.append("All ");
			giveTeX(byPhase, 0, sb);
			sb.append("\\\\\n");
			sb.append("\\hline");

			for(int level = 1; level*16<maxTag; ++level) {
				sb.append(" $").append(level).append("$ ");
				giveTeX(counts, level*16, sb);
				sb.append("\\\\\n");

			}
			sb.append("\\hline\n\\end{"+TABLE+"}");
			return sb.toString();			
		}
		private void giveString(long[] counts, int offset, StringBuilder ret) {
			double sum = 0;
			for(int i=0; i<16; ++i)
				sum += counts[offset+i];
			if(sum==0) return;
			for(int a = 0; a<16; ++a) {
				long c = counts[offset+a];
				try {
					int phase = ChonkerNode.decodePhase(a);
					int prio = ChonkerNode.decodePrio(a);
					switch(phase) {
					case 0: ret.append("Balancing ").append(prio); break;
					case 1: ret.append("Caterpillar"); break;
					case 2: ret.append("Diffbit ").append(prio); break;
					}
					ret.append(": ").append(c/sum).append("\n");
				}catch(IllegalArgumentException e) {

				}
			}

		}

		private void giveTeX(long[] counts, int offset, StringBuilder ret) {
			double sum = 0;
			for(int i=0; i<16; ++i)
				sum += counts[offset+i];
			if(sum==0) return;
			for(int a = 0; a<16; ++a) {
				long c = counts[offset+a];
				try {
					ChonkerNode.decodePhase(a);
					ChonkerNode.decodePrio(a);
					ret.append(" & $").append(String.format("%6.4f", 100*c/sum)).append("$ ");
				}catch(IllegalArgumentException e) {

				}
			}

		}
	}
	static class SingleSizeResult{
		int levels;
		double[] averageWeightPerLevel;
		double[] stdDevWeightPerLevel;
		long[] minWeightPerLevel;
		long[] maxWeightPerLevel;
		long[] maxSegmentWeightPerLevel;
		double[] minPairWeightPerLevel;
		public SingleSizeResult(ChonkerNode<?> root) {
			levels = root.level();
			averageWeightPerLevel = new double[levels];
			stdDevWeightPerLevel = new double[levels];
			minWeightPerLevel = new long[levels];
			maxWeightPerLevel = new long[levels];
			maxSegmentWeightPerLevel = new long[levels];
			minPairWeightPerLevel = new double[levels];
			for(int level = 0; level<levels; ++level) {
				int tag = ChonkerNode.encodeLevelTag(level, 2, 5);
				double weightSum = 0;
				double weightSquareSum = 0;
				long minWeight = Long.MAX_VALUE;
				long maxWeight = Long.MIN_VALUE;
				long maxSegmentWeight = Long.MIN_VALUE;
				double pairWeightSum = 0;
				int count = 0;
				long lastWeight = -1;
				for(
						ChonkerTreeZipper<?> at = new ChonkerTreeZipper<>(root).leftMost(tag);
						!at.isEnd();
						at = at.right(tag), count++
						) {
					long weight = at.node.weight();
					weightSum += weight;
					weightSquareSum += weight*weight;
					if(weight<minWeight)
						minWeight = weight;
					if(weight>maxWeight)
						maxWeight = weight;
					long segmentWeight;
					if(at.node instanceof Caterpillar) {
						segmentWeight = ((Caterpillar<?>)at.node).getSegment().weight();
					}else {
						segmentWeight = weight;
					}
					if(segmentWeight>maxSegmentWeight)
						maxSegmentWeight = segmentWeight;
					if(lastWeight>=0) {
						pairWeightSum += weight + lastWeight;
					}
					lastWeight = weight;
				}
				averageWeightPerLevel[level] = weightSum / count;
				stdDevWeightPerLevel[level] = Math.sqrt(weightSquareSum / count - averageWeightPerLevel[level]*averageWeightPerLevel[level]);
				minWeightPerLevel[level] = minWeight;
				maxWeightPerLevel[level] = maxWeight;
				maxSegmentWeightPerLevel[level] = maxSegmentWeight;
				minPairWeightPerLevel[level] = count<=1?0:pairWeightSum / (count-1);

			}
		}
	}
	static class SingleLocalityResult{
		int levels;
		long[] differentChunksLeftOrig;
		long[] differentChunksRightOrig;
		long[] differentChunksLeftModi;
		long[] differentChunksRightModi;
		long[] differentBitsLeft;
		long[] differentBitsRight;
		public <T extends ChonkersMonoidData<T>> SingleLocalityResult(ChonkerNode<T> original, ChonkerNode<T> modified, long modiIndex) {
			modiIndex*=32;
			levels = Math.min(original.level(), modified.level());
			int commonTag = ChonkerNode.encodeLevelTag(levels-1, 2, 5);
			differentChunksLeftOrig = new long[levels];
			differentChunksRightOrig = new long[levels];
			differentChunksLeftModi = new long[levels];
			differentChunksRightModi = new long[levels];
			differentBitsLeft= new long[levels];
			differentBitsRight= new long[levels];
			for(int level = 1; level<levels; ++level) {
				int tag = ChonkerNode.encodeLevelTag(level, 2, 5);
				ChonkerTreeZipper<T> oz = new ChonkerTreeZipper<T>(original).leftMost(commonTag);
				ChonkerTreeZipper<T> mz = new ChonkerTreeZipper<T>(modified).leftMost(commonTag);
				long skipped = 0;
				long lastWeight = 0;

				for(int cLevel = levels-1; cLevel >= level; --cLevel) {
					int ctag = ChonkerNode.encodeLevelTag(cLevel, 2, 5);
					oz = oz.leftMost(ctag);
					mz = mz.leftMost(ctag);
					while(!oz.isEnd() &&!mz.isEnd()) {
						if(!oz.node.equals(mz.node))
							break;
						if(skipped>=modiIndex)
							break;
						skipped += lastWeight = oz.node.weight();
						oz = oz.right(ctag);
						mz = mz.right(ctag);
					}
				}
				lastWeight = Math.min(oz.node.weight(), mz.node.weight());
				differentBitsLeft[level] = Math.max(0, modiIndex - skipped - lastWeight);

				long differentLeftOrig = 0;
				long differentBits;
				differentBits = skipped;
				while(!oz.isEnd() && differentBits < modiIndex) {
					differentBits += oz.node.weight();
					differentLeftOrig++;
					oz = oz.right(tag);
				}
				long differentLeftModi = 0;
				differentBits = skipped;
				while(!mz.isEnd() && differentBits < modiIndex) {
					differentBits += mz.node.weight();
					differentLeftModi++;
					mz = mz.right(tag);
				}
				long target = original.weight() - modiIndex;

				oz = new ChonkerTreeZipper<>(original).rightMost(commonTag);
				mz = new ChonkerTreeZipper<>(modified).rightMost(commonTag);
				lastWeight = 0;
				skipped = 0;
				for(int cLevel = levels-1; cLevel >= level; --cLevel) {
					int ctag = ChonkerNode.encodeLevelTag(cLevel, 2, 5);

					oz = oz.rightMost(ctag);
					mz = mz.rightMost(ctag);
					while(!oz.isEnd() &&!mz.isEnd()) {
						if(!oz.node.equals(mz.node))
							break;
						if(skipped >= target)
							break;
						skipped += lastWeight = oz.node.weight();
						oz = oz.left(ctag);
						mz = mz.left(ctag);
					}
				}				
				lastWeight = Math.min(oz.node.weight(), mz.node.weight());

				long differentRightOrig = 0;
				differentBits = skipped;
				differentBitsRight[level] = Math.max(0, target - skipped - lastWeight);
				while(!oz.isEnd() && differentBits < target) {
					differentBits += oz.node.weight();
					differentRightOrig++;
					oz = oz.left(tag);
				}
				long differentRightModi = 0;
				differentBits = skipped;
				target = modified.weight() - modiIndex;
				while(!mz.isEnd() && differentBits < target) {
					differentBits += mz.node.weight();
					differentRightModi++;
					mz = mz.left(tag);
				}
				differentChunksLeftOrig[level] = differentLeftOrig;
				differentChunksRightOrig[level] = differentRightOrig;
				differentChunksLeftModi[level] = differentLeftModi;
				differentChunksRightModi[level] = differentRightModi;

			}

		}
	}
	static class UniqueSubstring{
		final CharSequence container;
		final int offset;
		int length;
		final int hashCode;
		public UniqueSubstring(CharSequence container, int offset, int length, int hashCode) {
			if(offset + length >  container.length())
				throw new IllegalArgumentException();
			this.container = container;
			this.offset = offset;
			this.length = length;
			this.hashCode = hashCode;
		}
		@Override
		public int hashCode() {
			return hashCode;
		}
		@Override
		public boolean equals(Object o) {
			if(o == this)
				return true;
			if(o==null)
				return false;
			if(o instanceof UniqueSubstring) {
				UniqueSubstring a = (UniqueSubstring) o;
				if(hashCode!=a.hashCode)
					return false;
				if(length!=a.length)
					return false;
				if(a.container==container && offset==a.offset)
					return true;
//				if(container instanceof Yarn) {
//					return ((Yarn)container).substringEquals(offset, length, a.container, a.offset);
//				}
//				if(a.container instanceof Yarn) {
//					return ((Yarn)a.container).substringEquals(a.offset, length, container, offset);
//				}
				for(int i=0; i<length; ++i) {
					if(container.charAt(offset + i) != a.container.charAt(a.offset+i))
						return false;
				}
				return true;
			}
			return false;
			
		}
	}
	static class CompressibilityResult{
		ArrayList<CompressibilityLayerResult> ls = new ArrayList<>();
		public void add(Yarn y, CharSequence orig, List<HashSet<UniqueSubstring>> unique) {
			int neededLayers = y.heComin.level();
			if(ls.size()<=neededLayers || unique.size()<=neededLayers) {
				synchronized (this) {
					while(ls.size()<= neededLayers) {
						if(ls.size()==0)
							ls.add(new CompressibilityLayerResult(ls.size()));
						else
							ls.add(ls.get(ls.size()-1).clone(ls.size()));
					}
					while(unique.size() <= neededLayers)
						unique.add(new HashSet<>());
				}
			}
			for(int i=0; i<ls.size(); ++i) {
				ls.get(i).addYarn(y, orig, unique.get(i));
			}
		}
		public CompressibilityResult add(Corpus<?> corpus, List<HashSet<UniqueSubstring>> unique) {
			AtomicInteger i = new AtomicInteger();
			AtomicLong s = new AtomicLong();

			corpus.data.parallelStream().forEach(str->{
				Yarn orig = Yarn.of(str);
				add(orig, str, unique);

				long ss = s.addAndGet(orig.longLength());
				int ii = i.incrementAndGet();
				if(ii%10==0) {
					System.out.println("Dedup: processed "+ii+" strings, total chars: "+ss);
					System.gc();
				}
			});
			return this;
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			for(CompressibilityLayerResult l: ls)
				sb.append(l.toString()).append("\n");
			return sb.toString();
		}

		public String toTeX() {
			StringBuilder sb = new StringBuilder();
			sb.append("\\begin{tabular}{r|r|r|r|r|r|}\n");
			sb.append("layer & \\#chunks & \\#unique & caterpillars & compressed & ratio \\\\\n");
			sb.append("\\hline\n");
			for(CompressibilityLayerResult l: ls) {
				l.giveTeX(sb);
				sb.append("\\\\\n");
			}
			sb.append("\\hline\n");
			sb.append("\\end{tabular}\n");
			sb.append("\n total utf8 bytes: "+ls.get(0).totalLength/8);
			return sb.toString();
		}

	}
	static class CompressibilityLayerResult implements Cloneable{
		int layerIndex;
		int levelTag;
		public long totalChunks;
		public long uniqueChunks;
		public long absoluteUnit;
		public long totalUniqueLength;
		long totalLength;
		public long caterpillarLengths;

		public String toString() {
			long ts = totalLength/8;
			long cs = (long)Math.ceil(compressedSize()/8);
			StringBuilder sb = new StringBuilder();
			sb.append("Layer: ").append(layerIndex).append('\n');
			sb.append("\n  Total chunks: "+totalChunks);
			sb.append("\n  Total length: "+ts);
			sb.append("\n  Unique chunks:").append(uniqueChunks);
			sb.append("\n  Bits for caterpillar counts: "+(int)Math.ceil(caterpillarLengths));
			sb.append("\n  Compressed length: "+cs);
			sb.append("\n  ratio: "+(cs/(double)ts));
			return sb.toString();
		}
		public void giveTeX(StringBuilder sb) {
			long ts = totalLength/8;
			long cs = (long)Math.ceil(compressedSize()/8);
			sb.append(String.format(
					"$%d$  & $%s$ & $%s$ & $%s$ & $%s$ & $%s$ & $%.3f$", 
					layerIndex,
					sci(totalChunks, 2),
					sci(ts, 2),
					sci(uniqueChunks, 2),
					sci((long)Math.ceil(caterpillarLengths/8), 2),
					sci(cs, 2),
					(double)(cs/(double)ts)
					));
		}

		private static String sci(long n, int precision) {
			String direct = String.valueOf(n);
			int directWidth= direct.length();
			int exponent = (int) Math.floor(Math.log(n)/Math.log(10));
			int expLength = String.valueOf(exponent).length();
			double mantissa = n * Math.pow(10, -exponent);
			String mantissaString = String.format("%."+precision+"f", mantissa);
			double sciWidth = mantissaString.length() + 2 + expLength*0.7;
			if(sciWidth<directWidth) {
				return mantissaString+"\\cdot 10^{"+exponent+"}";
			}
			return direct;
		}
		public CompressibilityLayerResult(int layerIndex) {
			this.layerIndex = layerIndex;
			this.levelTag = ChonkerNode.encodeLevelTag(layerIndex, 2, 5);
			this.absoluteUnit = Yarn.cc.absoluteUnit(layerIndex);
		}
		public CompressibilityLayerResult clone(int layerIndex) {
			CompressibilityLayerResult ret;
			try {
				ret = (CompressibilityLayerResult) clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
			ret.layerIndex = layerIndex;
			ret.levelTag = ChonkerNode.encodeLevelTag(layerIndex, 2, 5);
			ret.absoluteUnit = Yarn.cc.absoluteUnit(layerIndex);
			return ret;
		}

		public void addNonUnique(ChonkerNode<?> n) {
			long utf8 = utf8Bytes(n)*8;
			synchronized (this) {

			}{
				totalChunks++;
				totalLength+=utf8;
			}
		}

		public void addYarn(Yarn y, CharSequence orig, HashSet<UniqueSubstring> unique) {
			ChonkerTreeZipper<?> at = new ChonkerTreeZipper<>(y.heComin).leftMost(levelTag);
			int position = 0;
			while(!at.isEnd()) {
				add(at.node, orig, position, unique);
				position += at.node.weight()/32;
				at = at.right(levelTag);
			}
		}

		public void add(ChonkerNode<?> node, CharSequence orig, int offset, HashSet<UniqueSubstring> unique) {
			boolean wasFirst;
			ChonkersMonoidData<?> md = node.getMonoidData();
			UniqueSubstring key = new UniqueSubstring(orig, offset, (int)(md.weight()/32), md.hashCode());
			synchronized (unique) {
				wasFirst = unique.add(key);
			}
			if(wasFirst) {
				addUnique(node);
			}else {
				addNonUnique(node);
			}
		}
		public void addUnique(ChonkerNode<?> n) {
			if(n instanceof Caterpillar) {
				Caterpillar<?> c = (Caterpillar<?>) n;
				long segUtf8 = utf8Bytes(c.getSegment())*8;
				synchronized (this) {
					addUniqueCaterpillar(segUtf8, c.repetitions());
					totalLength += c.repetitions() * segUtf8;
				}

			}else {
				long utf8 = utf8Bytes(n)*8;
				synchronized (this) {
					addUnique(utf8);
					totalLength += utf8;
				}
			}
		}
		long utf8Bytes(ChonkerNode<?> n) {
			if(n instanceof CharLeaf) {
				CharLeaf c = (CharLeaf) n;
				int codePoint = c.value;
				if (codePoint <= 0x7F) {
					return 1;
				} else if (codePoint <= 0x7FF) {
					return 2;
				} else if (codePoint <= 0xFFFF) {
					return 3;
				} else {
					return 4;
				}
			}
			if(n instanceof Caterpillar<?>) {
				Caterpillar<?> c = (Caterpillar<?>)n;
				return utf8Bytes(c.getSegment())*c.repetitions();
			}
			long r = 0;

			for(long i = 0; i<n.numChildren(); ++i) {
				r += utf8Bytes(n.getChild(i));
			}
			return r;

		}
		public void addUnique(long length) {
			totalUniqueLength += length;
			uniqueChunks++;
			totalChunks++;
		}
		public void addUniqueCaterpillar(long period, long repetitions) {
			totalUniqueLength += period;
			uniqueChunks++;
			totalChunks++;

			caterpillarLengths += Math.ceil(Math.log(repetitions));
		}
		public double compressedSize() {
			return totalUniqueLength // The unique contents
					+ uniqueChunks * Math.ceil(Math.log(absoluteUnit-1)/(32*Math.log(2))) // the lengths of the unique contents 
					+ caterpillarLengths //The repetition counts for caterpillars
					+ totalChunks * Math.log(uniqueChunks)/Math.log(2)
					;
		}



	}

	static class Corpus<S extends CharSequence>{
		List<S> data = new ArrayList<>();
		public final Function<? super CharSequence, ? extends S> inject;
		public Corpus(Function<? super CharSequence, ? extends S> inject) {
			this.inject=inject;
		}
		public Corpus(List<? extends CharSequence> copyThis, Function<? super CharSequence, ? extends S> inject) {
			this.inject=inject;
			AtomicInteger i = new AtomicInteger();
			AtomicLong s = new AtomicLong();
			this.data = copyThis.parallelStream().map(text->{
				long ss = s.addAndGet(text.length());
				int ii = i.incrementAndGet();
				if(ii%10==0) {
					System.out.println("Converted "+ii+" strings, total chars: "+ss);
					System.gc();
				}
				return inject.apply(text);
			}).collect(Collectors.toList());
		}
		public Corpus<Yarn> toYarnCorpus() {
			return  new Corpus<Yarn>(data, Yarn::of);
		}
		public Corpus<S> load(File filePath, int max, Predicate<? super String> filenameFilter) throws IOException {
			//Recursively load files from filePath

			Path p = filePath.toPath();
			Files.walkFileTree(p, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 100, new FileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					System.out.println(dir);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if(filenameFilter.test(file.getName(file.getNameCount()-1).toString()))
						addFile(file.toFile());
					if(max>=0 && data.size()>=max)
						return FileVisitResult.TERMINATE;
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
			});

			return this;
		}

		public void addFile(File file) throws FileNotFoundException, IOException {
			try(
					FileInputStream fis = new FileInputStream(file);
					InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
					BufferedReader br = new BufferedReader(isr)) 
			{
				String line;
				StringBuilder content = new StringBuilder();
				while((line=br.readLine())!=null) {
					content.append(line).append("\n");
				}
				if(content.length()>=10)
					data.add(inject.apply(content));
			}
		}
		public long totalCharCount() {
			long ret = 0;
			for(S s: data)
				ret += s.length();
			return ret;
		}




	}

	public static Corpus<Yarn> randomCorpus(int n, int l, long seed) {
		AtomicInteger i = new AtomicInteger();
		AtomicLong s = new AtomicLong();
		Corpus<Yarn> ret = new Corpus<Yarn>(Yarn::of);
		Random rs = new Random(seed);
		ArrayList<Long> seeds = new ArrayList<>();
		for(int c=0; c<n; c++) {
			seeds.add(rs.nextLong());
		}
		ret.data.addAll(seeds.parallelStream().map(seed2->{
			Random r = new Random(seed2);
			StringBuilder sb = new StringBuilder();
			for(int j=0; j<l; j++) {
				sb.append((char)(r.nextInt(0x100)));
			}
			long ss = s.addAndGet(sb.length());
			int ii = i.incrementAndGet();
			if(ii%100==0) {
				System.out.println("Generated "+ii+" random yarns, total chars: "+ss);
			}
			return Yarn.of(sb);
		}).collect(Collectors.toList()));
		return ret;

	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.US);
//		Corpus<?> corpus = defaultFictionCorpus(-1);
		Corpus<?> corpus = defaultKernelCorpus(-1);
//		Corpus<?> corpus = randomCorpus(10000, 10000, 4324324);
		System.out.println("Corpus loaded: "+corpus.data.size()+" strings, "+corpus.totalCharCount()+" chars");
		//		corpus = corpus.toYarnCorpus();

		if(true) {
			CompressibilityResult dedupResult = new CompressibilityResult().add(corpus, new ArrayList<>());
			System.out.println(dedupResult);
			System.out.println(dedupResult.toTeX());
			return;
		}

		GaussianSizeResult sizeResult = evalSizeGaussian(corpus);
		System.out.println(sizeResult);

		GaussianLocalityResult localityResult = evalLocalityGaussian(corpus);
		System.out.println(localityResult);

		PhaseCensus census = new PhaseCensus(corpus);
		System.out.println(census.toString());

		System.out.println(localityResult.toTeX(true));
		System.out.println(sizeResult.toTeX(true));
		System.out.println(localityResult.toTeX(false));
		System.out.println(sizeResult.toTeX(false));
		System.out.println(census.toTeX());
	}

	static Corpus<String> defaultFictionCorpus(int max) throws IOException {
		//Frank Fischer and Jannik Strötgen. 2017. Corpus of German-Language Fiction. https://doi.org/10.6084/m9.figshare.4524680.v1
		/*
		 @article{Fischer2017,
				author = "Frank Fischer and Jannik Strötgen",
				title = "{Corpus of German-Language Fiction (txt)}",
				year = "2017",
				month = "1",
				url = "https://figshare.com/articles/dataset/Corpus_of_German-Language_Fiction_txt_/4524680",
				doi = "10.6084/m9.figshare.4524680.v1"
				}
		 */

		return new Corpus<>(String::valueOf).load(
				new File(System.getProperty("user.home")+"/progio/trainingdata/Corpus of German-Language Fiction/"), 
				max,
				filename -> filename.endsWith(".txt"));
	}	
	static Corpus<String> defaultKernelCorpus(int max) throws IOException {
		//https://github.com/torvalds/linux/commit/b19a97d57c15643494ac8bfaaa35e3ee472d41da
		return new Corpus<>(String::valueOf).load(
				new File(System.getProperty("user.home")+"/progio/trainingdata/kernel"), 
				max,
				filename -> filename.endsWith(".c") || filename.endsWith(".h"));
	}	
	static GaussianSizeResult evalSizeGaussian(Corpus<?> corpus){
		return aggregateSizeGaussian(evalSize(corpus));
	}
	static GaussianLocalityResult evalLocalityGaussian(Corpus<?> corpus){
		return aggregateLocalityGaussian(evalLocality(corpus));
	}
	static GaussianSizeResult aggregateSizeGaussian(List<SingleSizeResult> rs){
		return new GaussianSizeResult(rs);
	}
	static GaussianLocalityResult aggregateLocalityGaussian(List<SingleLocalityResult> rs){
		return new GaussianLocalityResult(rs);
	}
	static List<SingleLocalityResult> evalLocality(Corpus<?> corpus){
		AtomicInteger i = new AtomicInteger();
		AtomicLong s = new AtomicLong();

		List<SingleLocalityResult> results = corpus.data.parallelStream().flatMap(str->{
			Yarn orig = Yarn.of(str);
			List<SingleLocalityResult> ret = new ArrayList<>();
			//			s+=text.length();
			long l = orig.length();

			long ss = s.addAndGet(l);
			int ii = i.incrementAndGet();
			if(ii%100==0) {
				System.out.println("Locality: processed "+ii+" strings, total chars: "+ss);
				System.gc();
			}


			for(int decile = 1; decile <=9; ++decile) {
				int pos = (int)(l*decile/10);
				Yarn modi = orig.replaceSubstring(pos, pos+1, "");
				SingleLocalityResult result = new SingleLocalityResult(orig.heComin, modi.heComin, pos);
				ret.add(result);
			}
			return ret.stream();
		}).collect(Collectors.toList());

		return results;
	}
	static List<SingleSizeResult> evalSize(Corpus<?> corpus){

		AtomicInteger i = new AtomicInteger();
		AtomicLong s = new AtomicLong();

		List<SingleSizeResult> results = corpus.data.parallelStream().map(str->{
			Yarn text = Yarn.of(str);
			//			s+=text.length();
			long l = text.length();

			long ss = s.addAndGet(l);
			int ii = i.incrementAndGet();

			ChonkerNode<?> root =text.heComin;
			SingleSizeResult result = new SingleSizeResult(root);

			if(ii%100==0) {
				//				try {
				//					text = null;
				//					for(int k=0; k<10; ++k) {
				//						System.gc();
				//						System.gc();
				//						System.gc();
				//						Thread.sleep(50);
				//					}
				//					System.out.println(Yarn.cc.canon.size());
				//					System.out.println(Yarn.cc.monoidCanon.size());
				//					System.in.read();
				//				} catch (IOException | InterruptedException e) {
				//					// TODO Auto-generated catch block
				//					e.printStackTrace();
				//				}
				System.gc();
				System.out.println(Yarn.cc.canon.size());
				System.out.println(Yarn.cc.monoidCanon.size());

				System.out.println("Size: Processed "+ii+" strings, total chars: "+ss);
			}
			return result;
		}).collect(Collectors.toList());

		return results;

	}
}