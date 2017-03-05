package flint
package service

import java.util.concurrent.{ Executors, ScheduledExecutorService }

package object mock {
  private[mock] val instanceSpecs = Seq(
    InstanceSpecs("t2.micro", 1, GiB(1), "0.013"),
    InstanceSpecs("c3.8xlarge", 32, GiB(52), InstanceStorageSpec(2, GiB(320)), "1.68"),
    InstanceSpecs("r3.large", 2, GiB(13), InstanceStorageSpec(1, GiB(32)), "0.166"),
    InstanceSpecs("r3.8xlarge", 32, GiB(236), InstanceStorageSpec(2, GiB(320)), "2.66"))

  private[mock] val instanceSpecsMap: Map[String, InstanceSpecs] =
    instanceSpecs.map(specs => specs.instanceType -> specs).toMap

  private[mock] lazy val simulationExecutorService: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(flintThreadFactory("mock-simulation-thread"))
}
