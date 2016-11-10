package flint
package service
package aws

import com.typesafe.config.ConfigFactory

import org.scalatest.FlatSpec

class UserDataSpec extends FlatSpec {
  import UserDataSpec._
  import AwsClusterService._

  behavior of "createUserData"

  it should "create user data without local storage" in {
    createUserData(
      SparkClusterRole.Master,
      DockerImage("test", "me"),
      Nil,
      testAwsConfig,
      testDockerConfig)
  }

  it should "create user data with local storage" in {
    val blockDeviceMappings = createBlockDeviceMappings(InstanceStorageSpec(2, GiB(320)))
    createUserData(
      SparkClusterRole.Master,
      DockerImage("test", "me"),
      blockDeviceMappings,
      testAwsConfig,
      testDockerConfig)
  }
}

object UserDataSpec {
  import collection.JavaConverters._

  private val testAwsConfig =
    ConfigFactory.parseMap(
      Map(
        "access_key"        -> "my_dumb_access_key",
        "secret_access_key" -> "my_dumb_secret_access_key",
        "region"            -> "us-east-1").asJava)

  private val testDockerConfig =
    ConfigFactory.parseMap(Map("auth" -> "noauth", "email" -> "whocares").asJava)
}
