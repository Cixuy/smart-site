/**
 * 摄像头模拟器
 *
 * 将本地视频文件循环推流（RTMP -> node-media-server），
 * 对外提供 HTTP-FLV 播放地址，模拟真实摄像头。
 *
 * 用法:
 *   npm install
 *   npm start -- --generate-sample        # 生成测试视频并启动
 *   npm start -- --video=d:/videos/a.mp4 --name=cam2
 *   npm start -- --transcode              # 源视频非 H.264/AAC 编码时转码
 *
 * 播放:
 *   HTTP-FLV:  http://127.0.0.1:8001/live/cam1.flv
 *   网页播放:  http://127.0.0.1:8001/
 *   可用 VLC / ffplay 打开 HTTP-FLV 地址
 */
const fs = require('fs');
const path = require('path');
const http = require('http');
const net = require('net');
const { spawn } = require('child_process');
const NodeMediaServer = require('node-media-server');
const ffmpegPath = require('ffmpeg-static');

const APP = 'live';
const DEFAULT_NAME = 'cam1';
const DEFAULT_RTMP_PORT = 1935;
const DEFAULT_HTTP_PORT = 8001;
const SAMPLE_FILE = path.join(__dirname, 'sample.mp4');

function printHelp() {
  console.log(`
用法: node start.js [选项]

选项:
  --video=<path>     要模拟播放的视频文件 (默认 ./sample.mp4)
  --name=<name>      流名称 (默认 ${DEFAULT_NAME})
  --app=<app>        推流 app 名 (默认 ${APP})
  --rtmp-port=<n>    RTMP 端口 (默认 ${DEFAULT_RTMP_PORT}，被占用时自动 +1)
  --http-port=<n>    HTTP/HTTP-FLV 端口 (默认 ${DEFAULT_HTTP_PORT}，被占用时自动 +1)
  --transcode        强制转码为 H.264/AAC (源视频编码不受支持时使用)
  --no-loop          不循环播放，播完即停
  --generate-sample  先用 ffmpeg 生成一段测试视频，再启动
  --check            启动后自动校验 HTTP-FLV 可用再退出(自检用)
  --help             显示本帮助
`);
}

function parseArgs(argv) {
  const opts = {
    video: null,
    name: DEFAULT_NAME,
    app: APP,
    rtmpPort: DEFAULT_RTMP_PORT,
    httpPort: DEFAULT_HTTP_PORT,
    rtmpPortExplicit: false,
    httpPortExplicit: false,
    transcode: false,
    loop: true,
    generateSample: false,
    check: false,
  };
  const list = argv.slice(2);
  const needValue = (i, key, val) => (val !== undefined && val !== '' ? val : list[++i]);
  for (let i = 0; i < list.length; i++) {
    const arg = list[i];
    const eq = arg.indexOf('=');
    const key = eq > 0 ? arg.slice(0, eq) : arg;
    const val = eq > 0 ? arg.slice(eq + 1) : undefined;
    switch (key) {
      case '--video': opts.video = needValue(i, key, val); break;
      case '--name': opts.name = needValue(i, key, val); break;
      case '--app': opts.app = needValue(i, key, val); break;
      case '--rtmp-port': opts.rtmpPort = parseInt(needValue(i, key, val), 10); opts.rtmpPortExplicit = true; break;
      case '--http-port': opts.httpPort = parseInt(needValue(i, key, val), 10); opts.httpPortExplicit = true; break;
      case '--transcode': opts.transcode = true; break;
      case '--no-loop': opts.loop = false; break;
      case '--generate-sample': opts.generateSample = true; break;
      case '--check': opts.check = true; break;
      case '--help':
      case '-h':
        printHelp();
        process.exit(0);
    }
  }
  return opts;
}

/** 检测端口是否被占用（与 node-media-server 同样监听所有接口，避免 IPv6 占用漏判） */
function isPortInUse(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.once('error', () => resolve(true));
    server.once('listening', () => server.close(() => resolve(false)));
    server.listen(port);
  });
}

function runFfmpeg(args, onClose) {
  const ff = spawn(ffmpegPath, args, { stdio: ['ignore', 'pipe', 'pipe'] });
  ff.stderr.on('data', (d) => {
    if (process.env.VERBOSE) process.stderr.write(d);
  });
  ff.on('close', (code) => onClose(code));
  return ff;
}

/** 生成一段 60 秒的彩色测试视频(带时间码与正弦音)，用于无素材时快速体验 */
function generateSample(file) {
  return new Promise((resolve, reject) => {
    console.log(`[INFO] 正在生成测试视频 -> ${file}`);
    const args = [
      '-f', 'lavfi',
      '-i', 'testsrc2=size=1280x720:rate=25:duration=60',
      '-f', 'lavfi',
      '-i', 'sine=frequency=440:sample_rate=44100:duration=60',
      '-c:v', 'libx264', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '128k',
      '-shortest',
      file,
    ];
    runFfmpeg(args, (code) => {
      if (code === 0) resolve();
      else reject(new Error(`ffmpeg 生成测试视频失败，退出码 ${code}`));
    });
  });
}

/** 循环推流到本地 RTMP，ffmpeg 意外退出后自动重连 */
function startPushing(opts, videoFile, streamUrl, stopRef) {
  const buildArgs = () => {
    const args = ['-re'];
    if (opts.loop) args.push('-stream_loop', '-1');
    args.push('-i', videoFile);
    if (opts.transcode) {
      args.push(
        '-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'zerolatency',
        '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '128k',
      );
    } else {
      args.push('-c:v', 'copy', '-c:a', 'copy');
    }
    args.push('-f', 'flv', streamUrl);
    return args;
  };

  const launch = () => {
    if (stopRef.stopped) return;
    console.log(`[PUSH] ffmpeg 推流 -> ${streamUrl}${opts.loop ? ' (循环)' : ''}`);
    const ff = spawn(ffmpegPath, buildArgs(), { stdio: ['ignore', 'pipe', 'pipe'] });
    ff.stderr.on('data', (d) => {
      if (process.env.VERBOSE) process.stderr.write(d);
    });
    ff.on('exit', (code, signal) => {
      if (stopRef.stopped) return;
      console.log(`[WARN] ffmpeg 退出 (code=${code}, signal=${signal})，2 秒后重连...`);
      setTimeout(launch, 2000);
    });
    stopRef.kill = () => {
      try { ff.kill('SIGKILL'); } catch (e) { /* ignore */ }
    };
  };

  launch();
}

/** 自检：向 HTTP-FLV 地址发起请求，读到 FLV 魔数即视为可用 */
function checkFlv(opts, app, name, timeoutMs = 40000, portOverride) {
  return new Promise((resolve, reject) => {
    const url = `http://127.0.0.1:${portOverride || opts.httpPort}/${app}/${name}.flv`;
    const started = Date.now();
    let done = false;

    const tryOnce = () => {
      if (done) return;
      const req = http.get(url, (res) => {
        let got = Buffer.alloc(0);
        res.on('data', (chunk) => {
          got = Buffer.concat([got, chunk]);
          if (got.length >= 3 && got.slice(0, 3).toString('ascii') === 'FLV') {
            done = true;
            console.log(`[OK] HTTP-FLV 校验通过: ${url}`);
            req.destroy();
            res.destroy();
            resolve(true);
          }
        });
        res.on('error', () => {});
      });
      req.setTimeout(2000, () => req.destroy());
      req.on('error', () => {});
      req.on('close', () => {
        if (done) return;
        if (Date.now() - started > timeoutMs) {
          done = true;
          reject(new Error(`等待 HTTP-FLV 可用超时(${timeoutMs / 1000}s)`));
        } else {
          setTimeout(tryOnce, 1000);
        }
      });
    };

    tryOnce();
  });
}

async function main() {
  const opts = parseArgs(process.argv);

  if (!ffmpegPath) {
    console.error('[ERR] ffmpeg-static 未正确安装，请先执行: npm install');
    process.exit(1);
  }

  const videoFile = opts.video ? path.resolve(opts.video) : SAMPLE_FILE;

  if (opts.generateSample) {
    try {
      await generateSample(videoFile);
    } catch (e) {
      console.error(`[ERR] ${e.message}`);
      process.exit(1);
    }
  }

  if (!fs.existsSync(videoFile)) {
    console.error(`[ERR] 视频文件不存在: ${videoFile}`);
    console.error('  请使用 --video=<路径> 指定视频，或先运行: node start.js --generate-sample');
    process.exit(1);
  }

  // 1. 端口自动避让：默认端口被占用时依次 +1；显式指定的端口冲突则报错
  let rtmpPort = opts.rtmpPort;
  let httpPort = opts.httpPort;
  for (let attempt = 0; attempt < 10; attempt++) {
    const [rtmpBusy, httpBusy] = await Promise.all([isPortInUse(rtmpPort), isPortInUse(httpPort)]);
    if (!rtmpBusy && !httpBusy) break;
    if (rtmpBusy) {
      if (opts.rtmpPortExplicit) {
        console.error(`[ERR] RTMP 端口 ${rtmpPort} 已被占用，请更换 --rtmp-port`);
        process.exit(1);
      }
      rtmpPort++;
    }
    if (httpBusy) {
      if (opts.httpPortExplicit) {
        console.error(`[ERR] HTTP 端口 ${httpPort} 已被占用，请更换 --http-port`);
        process.exit(1);
      }
      httpPort++;
    }
  }

  // 2. 启动 node-media-server（RTMP 入 + HTTP-FLV 出）
  const nms = new NodeMediaServer({
    logType: 3,
    rtmp: { port: rtmpPort, chunk_size: 60000, gop_cache: true, ping: 30, ping_timeout: 60 },
    http: {
      port: httpPort,
      allow_origin: '*',
      mediaroot: path.join(__dirname, 'media'),
      webroot: path.join(__dirname, 'public'),
    },
    auth: { enable: false },
  });
  nms.run();

  // 3. 推流
  const streamUrl = `rtmp://127.0.0.1:${rtmpPort}/${opts.app}/${opts.name}`;
  const flvUrl = `http://127.0.0.1:${httpPort}/${opts.app}/${opts.name}.flv`;
  const stopRef = { stopped: false, kill: null };

  console.log('');
  console.log('====================================================');
  console.log('  摄像头模拟器已启动');
  console.log('----------------------------------------------------');
  console.log(`  视频源:    ${videoFile}${opts.loop ? ' (循环)' : ''}`);
  console.log(`  RTMP 推流: ${streamUrl}`);
  console.log(`  HTTP-FLV:  ${flvUrl}`);
  console.log(`  网页播放:  http://127.0.0.1:${httpPort}/?stream=${opts.name}`);
  console.log('  提示: 可用 VLC / ffplay 打开上面的 HTTP-FLV 地址');
  console.log('====================================================');
  console.log('');

  setTimeout(() => startPushing(opts, videoFile, streamUrl, stopRef), 1000);

  function cleanup() {
    stopRef.stopped = true;
    if (stopRef.kill) stopRef.kill();
    try { nms.stop(); } catch (e) { /* ignore */ }
  }

  process.on('SIGINT', () => { console.log('\n[EXIT] 正在关闭...'); cleanup(); process.exit(0); });
  process.on('SIGTERM', () => { console.log('\n[EXIT] 正在关闭...'); cleanup(); process.exit(0); });

  if (opts.check) {
    try {
      await checkFlv(opts, opts.app, opts.name, 40000, httpPort);
      cleanup();
      process.exit(0);
    } catch (e) {
      console.error(`[FAIL] ${e.message}`);
      cleanup();
      process.exit(1);
    }
  }
}

main();
