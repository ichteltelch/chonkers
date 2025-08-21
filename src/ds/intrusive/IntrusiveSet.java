package ds.intrusive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * An IntrusiveSet manages an unordered set of elements. Each element must be associated with an index that can be
 * read and written using an {@link IndexGetter} and an {@link IndexSetter} passed to the constructor of the
 * IntrusiveSet instance. You must ensure that reading the index yields the value that was last written, or
 * a negative value if there was no previous write.<p>
 * You can also pass an {@link Eq}uivalence relation to the constructor that tells when two objects are considered the same.
 * The IndexGetter and IndexSetter must then manage one index per equivalence class. The equivalence relation defaults to
 * {@link Ords#identity()}. {@code null} is always considered distinct from other objects, even if the equivalence relation 
 * tells otherwise.
 * @author bb
 *
 * @param <E>
 */
public class IntrusiveSet<E> implements Iterable<E>{
	IndexGetter<? super E> getter;
	IndexSetter<? super E> setter;
	ArrayList<E> list;
	boolean containsNull;
	int modCount;
	public IntrusiveSet(IndexGetterAndSetter<? super E> getset) {
		getter=getset;
		setter=getset;
	}
	public IntrusiveSet(IndexGetter<? super E> get, IndexSetter<? super E> set) {
		getter=get;
		setter=set;
	}

	public boolean add(E e) {
		if(e==null) {
			boolean ret=!containsNull;
			containsNull=true;
			return ret;
		}
		int index=getter.getIndex(e);
		if(index>=0) {
			if(list==null || index>=list.size() || list.get(index)!=e)
				throw new IllegalArgumentException("The element already belongs to something else");
			return false;
		}
		modCount++;
		if(list==null) {
			list=new ArrayList<>();
			setter.setIndex(e, 0);
			list.add(e);
			return true;
		}
		setter.setIndex(e, list.size());;
		list.add(e);
		return true;
	}
	public boolean remove(E e) {
		if(e==null) {
			boolean ret=containsNull;
			containsNull=false;
			return ret;
		}
		if(list==null)
			return false;
		int index=getter.getIndex(e);
		int ls=list.size();
		if(index<0 || index>=list.size() || list.get(index)!=e)
			return false;
		modCount++;
		if(index==ls-1) {
			setter.setIndex(e, -1);
			list.remove(index);
		}else {
			E last = list.get(ls-1);
			setter.setIndex(last, index);
			setter.setIndex(e, -1);
			list.set(index, last);
			list.remove(ls-1);
		}
		return true;

	}
	public boolean contains(E e) {
		if(e==null)
			return containsNull;
		if(list==null)
			return false;
		int index = getter.getIndex(e);
		if(index<0 || index>=list.size() || list.get(index)!=e)
			return false;
		return true;
	}
	@Override
	public Iterator<E> iterator() {
		return new Iterator<E>() {
			int expectedModCount=modCount;
			int at = containsNull?-1:0;
			int len=list==null?0:list.size();
			boolean cantRemove=true;
			public boolean hasNext() {
				return at<len;
			}
			@Override
			public E next() {
				if(expectedModCount!=modCount)
					throw new ConcurrentModificationException();
				if(at==-1) {
					at=0;
					cantRemove=false;
					return null;
				}
				if(at>=len) {
					throw new NoSuchElementException();
				}
				cantRemove=false;
				return list.get(at++);
			}
			@Override
			public void remove() {
				if(expectedModCount!=modCount)
					throw new ConcurrentModificationException();
				if(cantRemove)
					throw new IllegalStateException();
				if(at==0) {
					containsNull=false;
					cantRemove=true;
					return;
				}else {
					cantRemove=true;
					at--;
					len--;
					IntrusiveSet.this.remove(list.get(at));
					expectedModCount=modCount;
					return;
				}
			}

		};
	}
	public int size() {
		int ret=containsNull?1:0;
		if(list!=null)
			ret+=list.size();
		return ret;
	}
	public boolean isEmpty(){
		return !containsNull && (list==null || list.isEmpty());
	}
	public void addAll(Collection<? extends E> es) {
		for(E e: es)
			add(e);
	}
	public void takeAll(IntrusiveSet<? extends E> es) {
		for(Iterator<? extends E> i = es.iterator(); i.hasNext(); ) {
			E e = i.next();
			i.remove();
			add(e);
		}
	}

	public void removeAll(Collection<? extends E> es) {
		for(E e: es)
			remove(e);
	}	
	public void retainAll(Collection<? extends E> es) {
		for(Iterator<E> i = iterator(); i.hasNext(); ) {
			if(!es.contains(i.next()))
				i.remove();
		}
	}
	public void clear() {
		if(list==null)
			return;
		for(E e: list)
			setter.setIndex(e, -1);
		list.clear();
	}

	public E elementAt(int j) {
		return list.get(j);
	}
	@Override
	public String toString() {
		return list.toString();
	}
}
