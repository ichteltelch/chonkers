package ds.chonker.tree;

import java.util.ArrayList;
import java.util.List;

public interface ChonkersMonoid<M extends ChonkersMonoidData<M>> {
	public M neutral();
	public M combine(M left, M right, ChonkerNode<M> contentHint);
	public M power(M base, long exp, ChonkerNode<M> contentHint);
	
	public default int computeAugHash(ChonkerNode<?> left, ChonkerNode<?> right) {
		int leftHash = left.augHash();
		int rightHash = right.augHash();
		
		long leftLength = left.weight();
		return combineAugHashes(leftHash, rightHash, leftLength);
	}
	public default int computeAugHash(ChonkerNode<?> node, long repetitions) {
		int nodeHash = node.augHash();
		long nodeLength = node.weight();
		int hash = 0;
		for(long pow = repetitions; pow>0; pow >>>= 1) {
			if((pow & 1)== 1) {
				hash = combineAugHashes(nodeHash, hash, nodeLength);
			}
			nodeHash = combineAugHashes(nodeHash, nodeHash, nodeLength);
			nodeLength*=2;
		}

		return hash;
	} 
	public default int combineAugHashes(int leftHash, int rightHash, long leftLength) {
		int square = 31;
		for(long pow = leftLength; pow>0; pow >>>= 1) {
			if((pow & 1)== 1) {
				rightHash = (rightHash * square);
			}
			square = (square * square);
		}
		return leftHash + rightHash;
	}
	public static final ChonkersMonoid<ChonkersMonoidData.Minimal> DEFAULT = new Minimal();
	public static final ChonkersMonoid<ChonkersMonoidData.WithUserMonoids> WITHUSERMONOIDS = new WithUserMonoids();

	public static abstract class Abstract<M extends ChonkersMonoidData<M>> implements ChonkersMonoid<M> {

		private static final boolean CHECK_HASH_CONSISTENCY = false;
		@Override
		public M neutral() {
			return null;
		}

		@Override
		public M combine(M left, M right, ChonkerNode<M> contentHint) {
			if(left==null) {
				if(right==null)
					return null;
				assert contentHint == null || contentHint.equalContent(right.content());
				return right;
			}
			if(right==null) {
				assert contentHint == null || contentHint.equalContent(left.content());
                return left;
			}
			assert contentHint!=null;
//			assert contentHint.isConcat(left.content(), right.content());
			long weight = left.weight() + right.weight();
			int augHash = computeAugHash(left.content(), right.content());
			if(CHECK_HASH_CONSISTENCY && contentHint.weight()<1000) {
				List<? extends ChonkerNode<?>> leaves = Rechonker.give(contentHint, 0, new ArrayList<>());
				int h = leaves.get(0).augHash();
				long ll = leaves.get(0).weight();
				for(int i = 1; i<leaves.size(); i++) {
					if(ll==left.weight())
						if(h!=left.augHash())
							throw new AssertionError("AugHash mismatch: expected "+augHash+", got "+h);

                    h = combineAugHashes(h, leaves.get(i).augHash(), ll);
                    ll += leaves.get(i).weight();
                }
				if(h!=augHash) {
					augHash = computeAugHash(left.content(), right.content());
	
					throw new AssertionError("AugHash mismatch: expected "+augHash+", got "+h);
				}
			}

			return make(weight, contentHint, augHash);
		}
		abstract M make(long weight, ChonkerNode<M> content, int augHash);
		@Override
		public M power(M base, long exp, ChonkerNode<M> contentHint) {
			if(base==null)
				return null;
			if(exp==0)
				return null;
			if(exp<0)
				throw new IllegalArgumentException("Exponent must be non-negative");
			if(exp==1)
				return base;
			assert contentHint!=null;
//			assert base.content().tiles(contentHint) == exp;
			long weight = base.weight() * exp;
			int augHash = computeAugHash(base.content(), exp);
			if(CHECK_HASH_CONSISTENCY && contentHint.weight()<1000) {
				List<? extends ChonkerNode<?>> leaves = Rechonker.give(contentHint, 0, new ArrayList<>());
				int h = leaves.get(0).augHash();
				long ll = leaves.get(0).weight();
				for(int i = 1; i<leaves.size(); i++) {
                    h = combineAugHashes(h, leaves.get(i).augHash(), ll);
                    ll += leaves.get(i).weight();
                }
				if(h!=augHash) {
					throw new AssertionError("AugHash mismatch: expected "+augHash+", got "+h);
				}
			}
			return make(weight, contentHint, augHash);
		}

	}

	

	public static class Minimal extends Abstract<ChonkersMonoidData.Minimal> {

		@Override
		ChonkersMonoidData.Minimal make(long weight,
				ChonkerNode<ChonkersMonoidData.Minimal> content, 
				int augHash) {
			return new ChonkersMonoidData.MinimalImpl(weight, content, augHash);
		}

		
		
	}
	public static class WithUserMonoids extends Abstract<ChonkersMonoidData.WithUserMonoids> {

		@Override
		ChonkersMonoidData.WithUserMonoids make(long weight,
				ChonkerNode<ChonkersMonoidData.WithUserMonoids> content, 
				int augHash) {
			return new ChonkersMonoidData.UserMonoidsImpl(weight, content, augHash);
		}

		
		
	}
}
