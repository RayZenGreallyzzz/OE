import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

script=Path(__file__).resolve().parents[1]/"tools"/"stage_uv44_uv56_private.py"
spec=importlib.util.spec_from_file_location("uv_stage",script)
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class StagingContractTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.uv44={f"assets/models/test-{n}.glb":bytes([n%255])
                   for n in range(125)}
        self.uv44["assets/index.html"]=b"<html>UV44</html>"
        self.uv56=dict(self.uv44)
        for name in mod.UV56_DELTA:
            self.uv56[name]=("UV56 "+name).encode()
        self.u44file=self.root/"44.apk"
        self.u56file=self.root/"56.apk"
        self.pack(self.u44file,self.uv44)
        self.pack(self.u56file,self.uv56)

    @staticmethod
    def pack(path,files):
        with zipfile.ZipFile(path,"w") as z:
            for name,data in files.items():
                z.writestr(name,data)

    def test_original_unchanged_and_delta_only(self):
        out=self.root/"assets"
        stats=mod.stage(self.u44file,self.u56file,out)
        self.assertEqual(stats["original_files"],len(self.uv44))
        self.assertEqual(stats["delta_files"],7)
        for name,data in self.uv44.items():
            self.assertEqual((out/"phoenix"/name).read_bytes(),data)
        actual={(p.relative_to(out/"phoenix-uv56-overlay")).as_posix()
                for p in (out/"phoenix-uv56-overlay").rglob("*") if p.is_file()}
        self.assertEqual(actual,set(mod.UV56_DELTA))

    def test_reject_builder_mutation_before_writing_assets(self):
        self.uv56["assets/models/test-0.glb"]=b"changed"
        self.pack(self.u56file,self.uv56)
        with self.assertRaisesRegex(ValueError,"Unexpected changes"):
            mod.stage(self.u44file,self.u56file,self.root/"assets")
        self.assertFalse((self.root/"assets").exists())

    def test_same_oe_app_with_mode_switch_and_terrain_guard(self):
        src=(Path(__file__).resolve().parents[1]/
             "android/src/main/java/game/oe/editor/PhoenixBuilderActivity.java").read_text()
        self.assertIn("private boolean landscapeMode=false;",src)
        self.assertIn("private void saveAndSwitch",src)
        self.assertIn('old.has("terrain")',src)
        self.assertIn('phoenix-uv56-overlay/assets/',src)
        self.assertIn('web.loadUrl(ORIGIN+(landscapeMode?',src)

if __name__=="__main__":
    unittest.main()
