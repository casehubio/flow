import { build, context } from "esbuild";
import { copyFileSync, mkdirSync } from "fs";

const isWatch = process.argv.includes("--watch");

const options = {
  entryPoints: ["src/index.ts"],
  bundle: true,
  outfile: "dist/app.js",
  format: "esm",
  target: "es2020",
  minify: !isWatch,
  sourcemap: isWatch,
};

mkdirSync("dist", { recursive: true });
copyFileSync("index.html", "dist/index.html");

if (isWatch) {
  const ctx = await context(options);
  await ctx.watch();
  console.log("Watching for changes...");
} else {
  await build(options);
}
