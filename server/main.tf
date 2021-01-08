provider "aws" {
  region = "us-east-2"
}

resource "aws_instance" "aws_server" {
  ami = "ami-0ff2fac4a31e58c24"
  instance_type = "t2.micro"
}
