// "Change signature of 'f(int...)' to 'f(String, int...)'" "true"
 public class S {
     void f(int... args) {
     f("",1,1)<caret>;
     }
 }
