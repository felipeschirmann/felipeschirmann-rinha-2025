# Vagrantfile
Vagrant.configure("2") do |config|
  config.vm.box = "debian/bookworm64" # Nome da imagem oficial do Debian 12
  config.vm.synced_folder ".", "/vagrant"
  config.vm.provider "virtualbox" do |vb|
     vb.memory = "4096" # 4GB de RAM
     vb.cpus = "2"      # 2 CPUs
  end
end
