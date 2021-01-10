provider "aws" {
  region = "us-east-2"
}

resource "aws_security_group" "server" {
  name = "minecraft_v2"

  tags = {
    Name = "minecraft_v2"
  }

  # SSH
  ingress {
    from_port = 22
    to_port = 22
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Minecraft port
  ingress {
    from_port = 25565
    to_port = 25565
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }


  # RCON port
  ingress {
    from_port = 25575
    to_port = 25575
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "aws_server" {
  ami = "ami-0b08bbbb93566d670"
  instance_type = "t3a.medium"
}

output "ip_address" {
  value = aws_instance.aws_server.public_ip
}
