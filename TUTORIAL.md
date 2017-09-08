# Getting Started with Flint on AWS and Jupyter

Flint was conceived at VideoAmp as a Web App to simplify the deployment and management of ad-hoc Spark clusters that could seamlessly access our existing production HDFS. VideoAmp had been using Jupyter to access Spark for some time, and we wanted a simple way to connect a Jupyter notebook to a Flint Spark cluster as well. We'll assume similar preconditions and goals in this tutorial for organizations that want to use Flint.

## Assumptions for this Tutorial

We'll assume that a company called Acme Widgets wants to deploy Flint into one of their existing AWS VPCs. The key aspects of their AWS infrastructure—as they relate to Flint—are as follows:

1. Their VPC is in AWS region US East 1.
1. They have a subnet in availability zone US East 1a where they want to deploy their Flint Spark clusters. For the purposes of this tutorial, we'll assume a subnet named `subnet-ab7f569c`.
1. They have a security group for accessing their HDFS servers. For the purposes of this tutorial, we'll assume a security group named `sg-73f460a8`.
1. They have an AWS access/secret key pair for launching EC2 instances and tagging resources. For the purposes of this tutorial, we'll assume a access key of `AKIABCMRQPSGDJFHABPA` and a secret access key of `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`.
1. They have an e-mail/auth token pair for accessing their docker hub repos. (This requirement may be deprecated or made optional. For now, it is required.) For the purposes of this tutorial, we'll assume an e-mail address of `docker@acmewidgets` and auth token of `fPc+g4hkgW5QApClWO7QzoZoQw==`.

Within `subnet-ab7f569c` they've deployed the following systems:

1. HDFS.
1. Jupyter.

## Creating Additional AWS Assets

In addition to the AWS assets Acme Widgets has already created, they will need to create an IAM instance profile and an SSH private key with which to launch Flint EC2 instances. After creating these, we'll assume that the former has the ARN `arn:aws:iam::198716430153:instance-profile/flint-spark` and the latter has the name `flint-spark`.

Acme will also need an EC2 instance in their VPC for running the Flint Web App. This can be a very small instance, such as a `t2.micro`. We'll assume this instance's DNS name is `flint.acme`. It must have Docker installed, since Flint is a Dockerized app.

## Creating a Spark Docker Image for Flint

Launching Spark with Flint requires a specially Dockerized image of Spark. Flint is compatible with any build of Spark 2.x with Hadoop built-in. This includes custom builds from source. Flint allows a user to select from any number of Spark images in their org's Spark image repo. For the purposes of this tutorial, we'll assume that Acme wants to use the standard Spark 2.2.0 distribution built with support for Hadoop 2.7 and later. Following the instructions at the [flint-spark](https://github.com/VideoAmp/flint-spark) project, Acme builds and pushes the image `acmewidgets/flint-spark:2.2.0-hadoop2.7-1`.

## Configuring the Flint Web App

Flint reads its application config using the [Typesafe Config](https://github.com/typesafehub/config) library. The application config is split into four files: `aws_service.conf`, `creds.conf`, `docker.conf` and `server.conf`. Each config file has a `-template.conf` file listing all of the required keys. In Acme Widgets's case, their `aws_service.conf` file looks like

```hocon
include "creds"

flint.aws {
  region="us-east-1"
  ami_id="ami-a4c7edb2" # Amazon Linux 2017.03.1 HVM (SSD) EBS-Backed 64-bit
  iam_instance_profile_specification {
    arn="arn:aws:iam::198716430153:instance-profile/flint-spark"
  }
  key_name="flint-spark"
  security_groups=["sg-73f460a8"]
  subnet_ids=["subnet-ab7f569c"]
  clusters_refresh {
    polling_interval="5s"
  }
  extra_instance_tags={}
}
```

Their `creds.conf` file looks like

```hocon
flint.docker {
  auth="fPc+g4hkgW5QApClWO7QzoZoQw=="
  email="docker@acmewidgets"
}

flint.aws {
  access_key="AKIABCMRQPSGDJFHABPA"
  secret_access_key="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"
}
```

Their `docker.conf` file looks like

```hocon
flint.docker {
  image_repo="acmewidgets/flint-spark"
}
```

Their `server.conf` file looks like

```hocon
include "aws_service"
include "docker"

flint.server {
  bind_address="0.0.0.0:8080"
  cluster_service="aws"
}
```

Flint uses [Log4j 2](https://logging.apache.org/log4j/2.x/) for logging. An example configuration file is provided at [conf/log4j2-example.xml](conf/log4j-example.xml). You can just copy this to `log4j2.xml` to use sensible defaults. See [Log4j Configuration](http://logging.apache.org/log4j/2.x/manual/configuration.html) for detailed information on configuring Log4j.

## Deploying the Flint Web App

The Flint Web App is a Docker image registered as [videoamp/flint-app](https://hub.docker.com/r/videoamp/flint-app/). It does not contain any configuration files. All configuration files should be put into a directory which will be mounted as a volume when the Docker container is launched. In the case of Acme Widgets, let's assume we'll run Flint as `root` and the configuration directory is `/root/flint-conf`. This directory contains all of the configuration files created above. Finally, we are ready to run Flint! Recall that our Flint server's DNS name is `flint.acme`. Suppose we want to run Flint 1.2.5. We can run Flint with the following command

```
docker run -d --name flint-app -v /root/flint-conf:/conf -p 8080:8080 -p 80:80 videoamp/flint-app:1.2.5 flint.acme:8080
```

Likewise, to stop the Flint container, run

```
docker stop flint-app && docker rm flint-app
```

Acme Widgets users can now browse to `http://flint.acme/` to access the Flint UI. If anything goes wrong, check the server logs with

```
docker logs flint-app
```

and check your browser's JavaScript console.

## Using a Flint Cluster in a Jupyter Scala Notebook

VideoAmp has developed tools to make using a Flint cluster in a [Jupyter Scala](https://github.com/jupyter-scala/jupyter-scala) notebook easy and seamless. If you want to access an existing HDFS cluster, your Jupyter host will need to have Hadoop and your site's HDFS configuration files installed. Otherwise, Spark will not be able to find your HDFS filesystem.

We've provided a [starter notebook](notebooks/flint_starter_notebook.ipynb) for accessing a Flint cluster. It includes comments that should make it self-explanatory. Essentially, all you need to connect a notebook to a Flint cluster is that cluster's master IP address. The bootstrapping process handles the rest.

This concludes our tutorial. We hope it has proven helpful in deploying Flint in your organization. We welcome your feedback on Slack.
