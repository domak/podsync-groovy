/**
 * Created by IntelliJ IDEA.
 * User: domak
 * Date: 05/12/10
 * Time: 15:59
 * To change this template use File | Settings | File Templates.
 */
def myMethod() {
  return null
}

def value = myMethod()
println "value: $value"
if (value) {
  println "value is not null"
} else {
  println "value is null"
}