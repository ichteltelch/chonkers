package ds.chonker.tree;

public interface UserMonoid<M> {
	public M combine(ChonkersMonoidData.WithUserMonoids left, ChonkersMonoidData.WithUserMonoids right);
	public M power(ChonkersMonoidData.WithUserMonoids base, long exponent);
	public M inject(ChonkerLeaf<ChonkersMonoidData.WithUserMonoids> leaf);
}
