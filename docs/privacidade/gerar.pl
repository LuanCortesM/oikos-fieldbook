use strict; use warnings; use MIME::Base64;
# Gera a pagina da politica de privacidade a partir de modelo.html:
#  - index.html: para publicar, com logo.png ao lado;
#  - ../../dist/politica-de-privacidade-previa.html: arquivo unico (logo embutido) para ver no celular.
chdir((__FILE__ =~ m{^(.*)[/\\]})[0] // '.');
sub slurp { my $f = shift; open(my $h, '<:raw', $f) or die "$f: $!"; local $/; my $s = <$h>; close $h; $s }
my $html = slurp('modelo.html');
(my $ridge = slurp('.itaguare_path.txt')) =~ s/\s+$//;
$html =~ s/__ITAGUARE__/$ridge/g;
my $data = $ARGV[0] // "19 de outubro de 2026";   # data da publicacao
$html =~ s/__DATA__/$data/g;
(my $pub = $html) =~ s/__LOGO__/logo.png/g;
open(my $o, '>:raw', 'index.html') or die; print $o $pub; close $o;
my $logo = 'data:image/png;base64,' . encode_base64(slurp('logo.png'), '');
(my $one = $html) =~ s/__LOGO__/$logo/g;
mkdir '../../dist' unless -d '../../dist';
open(my $p, '>:raw', '../../dist/politica-de-privacidade-previa.html') or die; print $p $one; close $p;
print "ok\n";
