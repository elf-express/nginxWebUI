const { test, expect } = require('@playwright/test');
const { execFileSync } = require('child_process');

const CONTAINER = 'nginx-webui-5.0.3';

/**
 * 在容器內執行指令
 */
function dockerExec(cmd) {
  try {
    return execFileSync('docker', ['exec', CONTAINER, 'sh', '-c', cmd], {
      encoding: 'utf-8',
      timeout: 10000,
    });
  } catch (e) {
    return e.stdout || '';
  }
}

test.describe('Real IP + Cloudflare + GeoIP', () => {

  test('realip.conf 應存在且包含 Cloudflare IP 段', () => {
    const conf = dockerExec('cat /etc/nginx/geoip/realip.conf');
    expect(conf).toContain('set_real_ip_from');
    expect(conf).toContain('real_ip_header CF-Connecting-IP');
    expect(conf).toContain('real_ip_recursive on');
  });

  test('應包含 Cloudflare IPv4 段', () => {
    const conf = dockerExec('cat /etc/nginx/geoip/realip.conf');
    // Cloudflare 的知名 IP 段
    expect(conf).toContain('103.21.244.0/22');
    expect(conf).toContain('104.16.0.0/13');
  });

  test('應包含 Cloudflare IPv6 段', () => {
    const conf = dockerExec('cat /etc/nginx/geoip/realip.conf');
    expect(conf).toContain('2400:cb00::/32');
  });

  test('應包含本機與 Docker 內網信任來源', () => {
    const conf = dockerExec('cat /etc/nginx/geoip/realip.conf');
    expect(conf).toContain('set_real_ip_from 127.0.0.1');
    expect(conf).toContain('set_real_ip_from 172.16.0.0/12');
    expect(conf).toContain('set_real_ip_from 10.0.0.0/8');
  });

  test('GeoLite2 Country mmdb 應存在', () => {
    const result = dockerExec('ls -la /etc/nginx/geoip/GeoLite2-Country.mmdb');
    expect(result).toContain('GeoLite2-Country.mmdb');
  });

  test('GeoLite2 City mmdb 應存在', () => {
    const result = dockerExec('ls -la /etc/nginx/geoip/GeoLite2-City.mmdb');
    expect(result).toContain('GeoLite2-City.mmdb');
  });

  test('GeoLite2 ASN mmdb 應存在', () => {
    const result = dockerExec('ls -la /etc/nginx/geoip/GeoLite2-ASN.mmdb');
    expect(result).toContain('GeoLite2-ASN.mmdb');
  });

  test('cron 排程應已設定', () => {
    const cron = dockerExec('cat /etc/crontabs/root');
    expect(cron).toContain('update-geoip-cf.sh');
  });

});
