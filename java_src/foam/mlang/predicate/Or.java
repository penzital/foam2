// WARNING: GENERATED CODE, DO NOT MODIFY BY HAND!
package foam.mlang.predicate;


public class Or extends foam.mlang.predicate.Nary implements foam.core.Serializable {
  private static final foam.core.ClassInfo classInfo_ = new foam.core.ClassInfoImpl()
    .setId("foam.mlang.predicate.Or");
  public foam.core.ClassInfo getClassInfo() {
    return classInfo_;
  }
  public static foam.core.ClassInfo getOwnClassInfo() {
    return classInfo_;
  }
  public boolean f(foam.core.FObject obj) {
    for ( int i = 0 ; i < getArgs().length ; i++ ) {
      if ( getArgs()[i].f(obj) ) return true;
    }
    return false;
    
  }
  public foam.mlang.predicate.Predicate partialEval() {
    return this;
  }
}