package ds.chonker.tree;

public class Diffbits {
	long order0;
	int[] higherOrders;
	int valid;
	
	public Diffbits(long order0, int maxOrders, Diffbits successor) {
		higherOrders = new int[maxOrders];
		this.order0 = order0;
		recompute(successor);
	}

	public void recompute(Diffbits successor) {
		valid = successor==null?100:successor.valid+1;
		{
			long diff = successor==null?1:(order0^successor.order0);
			if(diff==0) {
                throw new IllegalArgumentException("successive diffbits must be different");
			} else {
				int index = Long.bitCount(Long.lowestOneBit(diff)-1);
                boolean up = (order0 & (1<<index)) == 0;
                higherOrders[0] = (index << 1) | (up?1:0);
			}
		}
		for(int i=1; i<higherOrders.length && i<valid-1; i++) {
			int diff = successor==null?1:(higherOrders[i-1]^successor.higherOrders[i-1]);
			if(diff==0) {
//				orders[i]=-1;
				throw new IllegalArgumentException("successive diffbits must be different");
			}else {
				int index = Integer.bitCount(Integer.lowestOneBit(diff)-1);
				boolean up = (higherOrders[i-1] & (1<<index)) == 0;
				higherOrders[i] = (index << 1) | (up?1:0);
			}
		}
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		{
			sb.append(" : ");
			if(0<valid)
				sb.append(order0);
			else
				sb.append("_");
		}
		for(int i=0; i<higherOrders.length; ++i) {
			sb.append(" : ");
			if(i<valid-1)
				sb.append(higherOrders[i]);
			else
				sb.append("_");
		}
		return sb.toString();
	}
	public int getHighestOrderDiffbit() {
		if(higherOrders.length>=valid)
			throw new IllegalArgumentException("Highest order diffbit is invalid");
		return higherOrders[higherOrders.length-1];
	}
	public long getDiffbit(int order) {
		if(order>=valid)
			throw new IllegalArgumentException("Order "+order+" diffbit is invalid");
		if(order==0)
			return order0;
		return higherOrders[order-1];
	}
//	public static void main(String[] args) {
//		Random r = new Random(123);
//		int[] diffbits = new int[100];
//		for(int i=0; i<diffbits.length; i++) {
//			diffbits[i] = r.nextInt(60000);
//			if(i>0) {
//				if(diffbits[i-1]==diffbits[i]){
//					--i;
//				}
//			}
//		}
//		for(int i=0; i<10; ++i) {
//			for(int j=0; j<diffbits.length-1; j++) {
//				int diff = diffbits[j]^diffbits[j+1];
//				int index = Integer.bitCount(Integer.lowestOneBit(diff)-1);
//				boolean up = (diffbits[j] & (1<<index)) == 0;
//				diffbits[j] = index<<1 | (up?1:0);
//			}
//			diffbits[diffbits.length-1] = 1 - (diffbits[diffbits.length-1] & 1);
//			int[] histgram = new int[6];
//			for(int d : diffbits) {
//				if(d<histgram.length)
//                	++histgram[d];
//            }
//			System.out.println(Arrays.toString(histgram));
////			System.out.println(Arrays.toString(diffbits));
//		}
//	}
}
