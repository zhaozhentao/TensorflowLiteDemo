package tao.machine;

public class FindMax {

    public static final String[] chars = "京,沪,津,渝,冀,晋,蒙,辽,吉,黑,苏,浙,皖,闽,赣,鲁,豫,鄂,湘,粤,桂,琼,川,贵,云,藏,陕,甘,青,宁,新,0,1,2,3,4,5,6,7,8,9,A,B,C,D,E,F,G,H,J,K,L,M,N,P,Q,R,S,T,U,V,W,X,Y,Z,港,学,使,警,澳,挂,军,北,南,广,沈,兰,成,济,海,民,航,空".split(",");

    public static int findMax(float[] array) {
        if (array == null || array.length == 0) return -1;

        int largest = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[largest]) largest = i;
        }
        return array[largest] > 0.5 ? largest : 100;
    }
}
