package ds.chonker.tree;

public class ChonkerTreeZipper<M extends ChonkersMonoidData<M>> {
	static final public ChonkerTreeZipper<?> END = new ChonkerTreeZipper<>(null, null, 0);
	@SuppressWarnings("unchecked")
	public static <M extends ChonkersMonoidData<M>> ChonkerTreeZipper<M> end(){
		return (ChonkerTreeZipper<M>) END;
	}
	final public ChonkerNode<M> node;
	final ChonkerTreeZipper<M> parent;
	final long indexInParent;
	final int depth;
	public ChonkerTreeZipper(ChonkerNode<M> node) {
		this(node, null, 0);
	}

	public ChonkerTreeZipper(ChonkerNode<M> node, ChonkerTreeZipper<M> parent, long indexInParent) {
		if(parent!=null) {
			if(indexInParent<0 || indexInParent>=parent.node.numChildren())
				throw new IllegalArgumentException("indexInParent out of range");
		}
		this.node = node;
		this.parent = parent;
		this.indexInParent = indexInParent;

//		if(parent != null && node!=null) {
//			if(parent.node.getChild(indexInParent) != node) {
//				assert parent.node.getChild(indexInParent) == node;
//			}
//		}
		depth = parent==null? 0 : parent.depth+1;
	}
	public boolean isEnd() {
		return node==null;
	}
	public final ChonkerTreeZipper<M> commonAncestorUpToTag(ChonkerTreeZipper<M> other, int maxLevelTag) {
		ChonkerTreeZipper<M> self = this;
		while(self.depth>other.depth)
			self = self.parent;
		while(other.depth>self.depth)
			other = other.parent;
		while(true) {
			if(self.parent==null || other.parent==null)
				return null;
			if(self.node.layerTag() > maxLevelTag)
				return null;
			if(other.node.layerTag() > maxLevelTag)
				return null;
			if(self==other)
				break;
			self = self.parent;
			other = other.parent;
		}
		return self; 
	}
	public boolean isRoot() {
		return parent==null;
	}
	public ChonkerTreeZipper<M> left() {
		if(parent==null)
			return end();
		if(indexInParent==0) {
			return parent.left();
		}
		return new ChonkerTreeZipper<M>(parent.node.getChild(indexInParent-1), parent, indexInParent-1);
	}
	public ChonkerTreeZipper<M> right() {
		if(parent==null)
			return end();
		if(indexInParent>=parent.node.numChildren()-1) {
			return parent.right();
		}
		return new ChonkerTreeZipper<M>(parent.node.getChild(indexInParent+1), parent, indexInParent+1);
	}
	public ChonkerTreeZipper<M> leftMost(int downToLayerTag) {
		if(node==null)
			return this;
		if(node.layerTag()<=downToLayerTag)
			return this;
		return new ChonkerTreeZipper<M>(node.getChild(0), this, 0)
				.leftMost(downToLayerTag);
	}
	public ChonkerTreeZipper<M> rightMost(int downToLayerTag) {
		if(node==null)
			return this;
		if(node.layerTag()<=downToLayerTag)
			return this;
		return new ChonkerTreeZipper<M>(node.getChild(node.numChildren()-1), this, node.numChildren()-1)
				.rightMost(downToLayerTag);
	}
	public ChonkerTreeZipper<M> left(int downToLayerTag) {
		return left().rightMost(downToLayerTag);
	}
	public ChonkerTreeZipper<M> right(int downToLayerTag) {
		return right().leftMost(downToLayerTag);
	}
	public ChonkerTreeZipper<M> upToLayer(int layerTag) {
		if(node==null)
			return this;
		if(parent==null)
			return this;
		if(parent.node.layerTag()>layerTag)
			return this;
		return parent.upToLayer(layerTag);
	}
	public ChonkerTreeZipper<M> zipTo(long bitIndex) {
		return node.zipTo(this, bitIndex);

	}
	public String toString() {
		if(isEnd())
			return ".";
		return toString(-1, node.toString());
	}
	public String toString(long substIndex, String substText) {
		StringBuilder sb = new StringBuilder();
		sb.append("<");
		if(node.numChildren()==0)
			sb.append(node);
		for(int i=0; i<node.numChildren(); ++i) {
			if(i!=0)
				sb
				.append("[")
				.append(node.level())
				.append(":")
				.append(node.phase())
				.append(":")
				.append(node.prio())
				.append("]");
			if(i==substIndex)
				sb.append(substText);
			else
				sb.append(node.getChild(i));
		}
		sb.append(">");
		if(parent==null)
			return sb.toString();
		return parent.toString(indexInParent, sb.toString());
	}


	public ChonkerTreeZipper<M> discardLeft(ChonkerConfig<M> c) {
		if(parent==null)
			return this;
		ChonkerTreeZipper<M> dp = parent.discardLeft(c, indexInParent, node);
		if(dp.node.numChildren()==0 || dp.node==node)
			return dp;
//		return dp.leftMost(node.level());

		return new ChonkerTreeZipper<M>(node, dp, 0);
	}
	public ChonkerTreeZipper<M> discardRight(ChonkerConfig<M> c) {
		if(parent==null)
			return this;
		ChonkerTreeZipper<M> dp = parent.discardRight(c, indexInParent, node);
		if(dp.node.numChildren()==0 || dp.node==node)
			return dp;
//		return dp.rightMost(node.level());

		return new ChonkerTreeZipper<M>(node, dp, dp.node.numChildren()-1);
	}

	public ChonkerTreeZipper<M> discardLeft(ChonkerConfig<M> c, long ci, ChonkerNode<M> subst) {
		ChonkerNode<M> snode;
		if(ci==node.numChildren()-1) {
			if(parent==null)
				return new ChonkerTreeZipper<M>(subst);
			else
				return parent.discardLeft(c, indexInParent, subst);
		}else {
			if(subst==node.getChild(ci))
				snode = node.dropPrefixChildren(ci, c);
			else
				snode = node.dropPrefixChildren(ci, c).substChild(0, subst, c);	
		}
		if(parent==null)
			return new ChonkerTreeZipper<M>(snode);
		ChonkerTreeZipper<M> dp = parent.discardLeft(c, indexInParent, snode);
		if(dp.node.numChildren()==0 || dp.node==snode)
			return dp;
		
		if(dp.node.getChild(0)!=snode)
			parent.discardLeft(c, indexInParent, snode);
		
		return new ChonkerTreeZipper<M>(snode, dp, 0);
	}
	public ChonkerTreeZipper<M> discardRight(ChonkerConfig<M> c, long ci, ChonkerNode<M> subst) {
		ChonkerNode<M> snode;

		if(ci==0) {
			if(parent==null)
				return new ChonkerTreeZipper<M>(subst);
			else
				return parent.discardRight(c, indexInParent, subst);
		}else {
			ChonkerNode<M> dnode= node.dropSuffixChildren(node.numChildren()-1-ci, c);
			if(subst==node.getChild(ci))
				snode = dnode;
			else {
				snode = dnode.substChild(dnode.numChildren()-1, subst, c);
			}
		}
		if(parent==null)
			return new ChonkerTreeZipper<M>(snode);
		ChonkerTreeZipper<M> dp = parent.discardRight(c, indexInParent, snode);
		if(dp.node.numChildren()==0 || dp.node==snode)
			return dp;
		return new ChonkerTreeZipper<M>(snode, dp, dp.node.numChildren()-1);
	}

	public ChonkerTreeZipper<M> getRoot() {
		ChonkerTreeZipper<M> at = this;
		while(at.parent!=null)
			at = at.parent;
		return at;
	}


	public ChonkerTreeZipper<M> skipLeft(long length, int level) {
		ChonkerTreeZipper<M> at = this;
		while(length>0) {
			at = at.left(level);
			if(at.isEnd())
				throw new IllegalArgumentException("cannot skip left past end");
			length -= at.node.weight();
		}
		assert length == 0;
		return at;
	}
	public ChonkerTreeZipper<M> skipRight(long length, int level) {
		ChonkerTreeZipper<M> at = this;
		while(length>0) {
			at = at.right(level);
			if(at.isEnd())
				throw new IllegalArgumentException("cannot skip right past end");
			length -= at.node.weight();
		}
		assert length == 0;
		return at;
	}
}
