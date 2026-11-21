#!/usr/bin/env python3
"""
mc_inspect.py - Multi-Platform Minecraft & Modding Inspection Toolkit & Disassembler.

Supports:
- Minecraft versions: 1.21.1+ and forward-compatible across version upgrades
- Platforms: NeoForge, Fabric, Forge, and Vanilla mapped jars
- Dynamic platform auto-discovery across workspace subprojects (*-platform, build/moddev, loom-cache)
- Official Mojang-mapped Java sources directly from sources.jar
- Compiled bytecode via javap (with auto-resolved classpath)
- Class hierarchy, fields, constructors, and methods with smart short-name resolver
- Interactive REPL shell ('mc shell') with fuzzy command suggestions
- Game mechanics & Registry inspection:
    * Components (DataComponents.*)
    * Attributes (Attributes.*)
    * Properties (BlockStateProperties.*)
    * Packets (Clientbound* / Serverbound*)
    * Effects (MobEffects.*)
    * Potions (Potions.*)
    * Sounds (SoundEvents.*)
    * Particles (ParticleTypes.*)
    * Entities (EntityType.*)
    * Registries (BuiltInRegistries.*)
    * Tags (ItemTags, BlockTags, EnchantmentTags, etc.)
    * Capabilities (NeoForge Capabilities / Fabric Transfer / Forge)
    * Events (NeoForge / Forge / Fabric)
    * Extensions (NeoForge Extension interfaces)
- Fast source usages/references across the entire Minecraft codebase
- ANSI syntax highlighting & JSON structured output
"""

import argparse
import difflib
import glob
import json
import os
import re
import shlex
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

# Terminal color codes (ANSI)
USE_COLOR = sys.stdout.isatty() and os.environ.get("NO_COLOR") is None

CYAN = "\033[96m" if USE_COLOR else ""
GREEN = "\033[92m" if USE_COLOR else ""
YELLOW = "\033[93m" if USE_COLOR else ""
RED = "\033[91m" if USE_COLOR else ""
MAGENTA = "\033[95m" if USE_COLOR else ""
BOLD = "\033[1m" if USE_COLOR else ""
DIM = "\033[2m" if USE_COLOR else ""
RESET = "\033[0m" if USE_COLOR else ""

VALID_COMMANDS = [
    "search", "find", "source", "disasm", "javap", "info", "tree", "methods", "fields",
    "events", "extensions", "registries", "tags", "components", "attributes", "properties",
    "packets", "effects", "potions", "sounds", "particles", "entities", "capabilities", "caps",
    "usages", "references", "classpath", "status", "platform", "shell", "repl"
]


def get_repo_root() -> Path:
    cur = Path(__file__).resolve().parent
    while cur != cur.parent:
        if (cur / ".git").exists() or (cur / "settings.gradle").exists() or (cur / "settings.gradle.kts").exists():
            return cur
        cur = cur.parent
    return Path(__file__).resolve().parent.parent


def suggest_command(typed: str) -> Optional[str]:
    matches = difflib.get_close_matches(typed.lower(), VALID_COMMANDS, n=1, cutoff=0.5)
    return matches[0] if matches else None


# -----------------------------------------------------------------------------
# Jar Discovery & Indexing (Multi-Platform & Multi-Version)
# -----------------------------------------------------------------------------

class JarIndex:
    """Manages discovery and rapid lookup of classes, bytecode, and sources across platforms and versions."""

    def __init__(
        self,
        repo_root: Path,
        platform_pref: Optional[str] = None,
        custom_sources: Optional[str] = None,
        custom_merged: Optional[str] = None,
    ):
        self.repo_root = repo_root
        self.platform_pref = platform_pref or os.environ.get("MC_PLATFORM")
        self.custom_sources = custom_sources or os.environ.get("MC_SOURCES_JAR")
        self.custom_merged = custom_merged or os.environ.get("MC_MERGED_JAR")

        self.merged_jar: Optional[Path] = None
        self.sources_jar: Optional[Path] = None
        self.all_jars: List[Path] = []
        self._source_index: Set[str] = set()
        self._source_namelist: List[str] = []
        self._simple_name_map: Dict[str, List[str]] = {}

        self.discovered_platforms: List[Dict[str, Any]] = []
        self.active_platform_type: str = "Unknown"
        self.active_platform_version: str = "Unknown"
        self.active_mc_version: str = "Unknown"

        self._discover_all()

    def _discover_all(self):
        # 1. Scan candidate subprojects for platforms
        candidate_dirs: List[Path] = []
        for child in sorted(self.repo_root.iterdir()):
            if not child.is_dir() or child.name.startswith("."):
                continue
            if child.name.endswith("-platform") or "platform" in child.name.lower() or (child / "build.gradle").exists():
                candidate_dirs.append(child)

        # 2. Inspect artifact folders in each candidate subproject
        for cdir in candidate_dirs:
            p_info: Dict[str, Any] = {
                "name": cdir.name,
                "path": cdir,
                "sources_jars": [],
                "merged_jars": [],
                "other_jars": [],
                "platform_type": "Unknown",
            }
            # Search artifact patterns:
            # - NeoForge ModDevGradle: build/moddev/artifacts/*.jar
            # - Fabric Loom: build/loom-cache/**/*.jar, .gradle/loom-cache/**/*.jar
            # - General: build/libs/*.jar
            patterns = [
                cdir / "build" / "moddev" / "artifacts" / "*.jar",
                cdir / "build" / "loom-cache" / "**" / "*.jar",
                cdir / ".gradle" / "loom-cache" / "**" / "*.jar",
                cdir / "build" / "libs" / "*.jar",
            ]
            found_jars: List[Path] = []
            for pat in patterns:
                for match in glob.glob(str(pat), recursive=True):
                    found_jars.append(Path(match))

            for jar in found_jars:
                jname = jar.name.lower()
                if "extra" in jname or "resource" in jname:
                    continue
                if re.search(r"[-_.]sources\.jar", jname) or ("sources" in jname and "resource" not in jname):
                    p_info["sources_jars"].append(jar)
                elif any(k in jname for k in ("merged", "mapped", "minecraft-merged")):
                    p_info["merged_jars"].append(jar)
                else:
                    p_info["other_jars"].append(jar)

            if "neoforge" in cdir.name.lower():
                p_info["platform_type"] = "NeoForge"
            elif "fabric" in cdir.name.lower():
                p_info["platform_type"] = "Fabric"
            elif "forge" in cdir.name.lower():
                p_info["platform_type"] = "Forge"

            if p_info["sources_jars"] or p_info["merged_jars"]:
                self.discovered_platforms.append(p_info)

        # 3. Select active platform based on preference or best match
        selected: Optional[Dict[str, Any]] = None
        if self.platform_pref:
            pref_lower = self.platform_pref.lower()
            for p in self.discovered_platforms:
                if pref_lower in p["name"].lower() or pref_lower in p["platform_type"].lower():
                    selected = p
                    break

        if not selected and self.discovered_platforms:
            # Pick first with both sources and merged
            for p in self.discovered_platforms:
                if p["sources_jars"] and p["merged_jars"]:
                    selected = p
                    break
            if not selected:
                selected = self.discovered_platforms[0]

        if selected:
            if selected["sources_jars"] and not self.custom_sources:
                self.sources_jar = selected["sources_jars"][0]
            if selected["merged_jars"] and not self.custom_merged:
                self.merged_jar = selected["merged_jars"][0]
            for j in selected["merged_jars"] + selected["other_jars"]:
                if j not in self.all_jars:
                    self.all_jars.append(j)

        if self.custom_sources:
            self.sources_jar = Path(self.custom_sources)
        if self.custom_merged:
            self.merged_jar = Path(self.custom_merged)
            if self.merged_jar not in self.all_jars:
                self.all_jars.insert(0, self.merged_jar)

        # 4. Search global Gradle cache for Fabric / Forge / NeoForge if not yet found
        gradle_home = os.environ.get("GRADLE_USER_HOME", os.path.expanduser("~/.gradle"))
        if not self.sources_jar or not self.merged_jar:
            cache_roots = [
                os.path.join(gradle_home, "caches", "fabric-loom", "**", "*.jar"),
                os.path.join(gradle_home, "caches", "forge_gradle", "**", "*.jar"),
                os.path.join(gradle_home, "caches", "neoforged", "**", "*.jar"),
            ]
            for croots in cache_roots:
                for match in glob.glob(croots, recursive=True):
                    p = Path(match)
                    name_l = p.name.lower()
                    if not self.sources_jar and "sources" in name_l and "resource" not in name_l and ("minecraft" in name_l or "neoforge" in name_l or "fabric" in name_l):
                        self.sources_jar = p
                    elif not self.merged_jar and ("merged" in name_l or "mapped" in name_l) and ("sources" not in name_l or "resource" in name_l):
                        self.merged_jar = p
                        self.all_jars.append(p)

        # 5. Scan gradle cache for mods and common libraries (GeckoLib, JEI, REI, EMI, luava, etc.)
        cache_pattern = os.path.join(gradle_home, "caches", "modules-2", "files-2.1", "**", "*.jar")
        for match in glob.glob(cache_pattern, recursive=True):
            p = Path(match)
            name_l = p.name.lower()
            if any(k in name_l for k in ("geckolib", "jei", "rei", "emi", "luava", "architectury")) and "sources" not in name_l:
                if p not in self.all_jars:
                    self.all_jars.append(p)

        if not self.merged_jar and self.all_jars:
            self.merged_jar = self.all_jars[0]

        # 6. Index sources.jar
        if self.sources_jar and self.sources_jar.exists():
            try:
                with zipfile.ZipFile(self.sources_jar, "r") as z:
                    self._source_namelist = z.namelist()
                    for entry in self._source_namelist:
                        if entry.endswith(".java"):
                            cls_name = entry[:-5].replace("/", ".")
                            self._source_index.add(cls_name)
                            simple = cls_name.split(".")[-1]
                            self._simple_name_map.setdefault(simple.lower(), []).append(cls_name)
            except (zipfile.BadZipFile, OSError):
                pass

        # 7. Identify active platform and versions
        self._detect_environment_info(selected)

    def _detect_environment_info(self, selected_platform: Optional[Dict[str, Any]]):
        # Platform type
        is_neoforge = any("neoforge" in str(j).lower() for j in self.all_jars) or any("net.neoforged" in c for c in self._source_index)
        is_fabric = any("fabric" in str(j).lower() for j in self.all_jars) or any("net.fabricmc" in c for c in self._source_index)
        is_forge = any("forge" in str(j).lower() and "neoforge" not in str(j).lower() for j in self.all_jars)

        if is_neoforge:
            self.active_platform_type = "NeoForge"
        elif is_fabric:
            self.active_platform_type = "Fabric"
        elif is_forge:
            self.active_platform_type = "Forge"
        else:
            self.active_platform_type = "Vanilla Minecraft"

        # Loader version from jar names
        for j in self.all_jars:
            jname = j.name
            m_neo = re.search(r"neoforge-([0-9.]+)", jname)
            if m_neo:
                self.active_platform_version = m_neo.group(1)
                break
            m_fab = re.search(r"fabric-loader-([0-9.]+)", jname)
            if m_fab:
                self.active_platform_version = m_fab.group(1)
                break
            m_for = re.search(r"forge-([0-9.]+)", jname)
            if m_for:
                self.active_platform_version = m_for.group(1)
                break

        # Minecraft Version detection from DetectedVersion.java
        det_src = self.get_source_raw("net/minecraft/DetectedVersion.java")
        if det_src:
            m_ver = re.search(r'this\.name\s*=\s*"([^"]+)"', det_src)
            if m_ver:
                self.active_mc_version = m_ver.group(1)

        if self.active_mc_version == "Unknown":
            # Fallback regex on jar names
            for j in self.all_jars:
                m_mc = re.search(r"(?:minecraft|client|server)[-_](1\.[0-9]+(?:\.[0-9]+)?)", j.name)
                if m_mc:
                    self.active_mc_version = m_mc.group(1)
                    break
            if self.active_mc_version == "Unknown" and self.active_platform_version.startswith("21.1"):
                self.active_mc_version = "1.21.1"

    def get_classpath(self) -> str:
        sep = ";" if os.name == "nt" else ":"
        return sep.join(str(j) for j in self.all_jars)

    def resolve_class(self, name: str) -> str:
        """
        Resolves simple or partial class names to fully qualified names.
        Prioritizes primary game packages over reference/test generators.
        """
        if "." in name:
            return name
        matches = self._simple_name_map.get(name.lower(), [])
        if not matches:
            candidates = [c for c in self._source_index if name.lower() in c.split(".")[-1].lower()]
            if candidates:
                matches = candidates

        if len(matches) == 1:
            return matches[0]

        if len(matches) > 1:
            def rank_package(pkg: str) -> int:
                # Lower number = higher priority
                if "net.minecraft.world.item." in pkg or "net.minecraft.world.level.block." in pkg:
                    return 1
                if "net.minecraft.world.entity." in pkg or "net.minecraft.world.level." in pkg or "net.minecraft.world.effect." in pkg:
                    return 2
                if "net.minecraft.core." in pkg or "net.minecraft.sounds." in pkg or "net.minecraft.tags." in pkg:
                    return 3
                if "net.minecraft.client." in pkg or "net.minecraft.server." in pkg:
                    return 4
                if "net.minecraft.network." in pkg or "net.minecraft.resources." in pkg:
                    return 5
                if "net.neoforged." in pkg or "net.fabricmc." in pkg or "net.minecraftforge." in pkg:
                    return 6
                if "net.minecraft.references." in pkg or "net.minecraft.data." in pkg:
                    return 10
                return 8

            # Exact simple name matches first
            exact_matches = [c for c in matches if c.split(".")[-1].lower() == name.lower()]
            pool = exact_matches if exact_matches else matches
            pool.sort(key=rank_package)
            return pool[0]

        return name

    def find_classes(self, query: str, limit: int = 50) -> List[Tuple[str, str]]:
        pattern = re.compile(query, re.IGNORECASE)
        results = []

        for cls in sorted(self._source_index):
            if pattern.search(cls):
                results.append((cls, "sources.jar"))
                if len(results) >= limit:
                    return results

        for jar in self.all_jars:
            try:
                with zipfile.ZipFile(jar, "r") as z:
                    for entry in z.namelist():
                        if entry.endswith(".class") and "$" not in entry:
                            cls = entry[:-6].replace("/", ".")
                            if pattern.search(cls) and not any(r[0] == cls for r in results):
                                results.append((cls, jar.name))
                                if len(results) >= limit:
                                    return results
            except (zipfile.BadZipFile, OSError):
                continue
        return results

    def get_source_raw(self, zip_path: str) -> Optional[str]:
        if not self.sources_jar or not self.sources_jar.exists():
            return None
        try:
            with zipfile.ZipFile(self.sources_jar, "r") as z:
                if zip_path in z.namelist():
                    with z.open(zip_path) as f:
                        return f.read().decode("utf-8", errors="replace")
        except (zipfile.BadZipFile, OSError):
            pass
        return None

    def get_source(self, class_name: str) -> Optional[str]:
        resolved = self.resolve_class(class_name)
        top_class = resolved.split("$")[0]
        zip_path = top_class.replace(".", "/") + ".java"
        return self.get_source_raw(zip_path)


# -----------------------------------------------------------------------------
# Java Source & Syntax Highlighting
# -----------------------------------------------------------------------------

def highlight_java(text: str) -> str:
    """Lightweight ANSI syntax highlighting for Java sources."""
    if not USE_COLOR:
        return text

    keywords = (
        r"\b(public|protected|private|static|final|abstract|default|class|interface|enum|record|"
        r"extends|implements|throws|return|void|new|this|super|if|else|switch|case|break|"
        r"continue|for|while|do|try|catch|finally|throw|instanceof|synchronized|volatile|transient)\b"
    )
    types = r"\b(int|long|short|byte|float|double|boolean|char|String|Object|ItemStack|LivingEntity|Player|Level)\b"
    annotations = r"(@[A-Za-z0-9_]+)"
    strings = r"(\"[^\"]*\")"

    lines = []
    for line in text.splitlines():
        if line.strip().startswith("//"):
            lines.append(f"{DIM}{line}{RESET}")
            continue
        l = re.sub(keywords, f"{MAGENTA}\\1{RESET}", line)
        l = re.sub(annotations, f"{YELLOW}\\1{RESET}", l)
        l = re.sub(types, f"{CYAN}\\1{RESET}", l)
        l = re.sub(strings, f"{GREEN}\\1{RESET}", l)
        lines.append(l)
    return "\n".join(lines)


def extract_method_source(source_text: str, method_name: str) -> Optional[str]:
    lines = source_text.splitlines()
    target_pattern = re.compile(r"\b" + re.escape(method_name) + r"\s*\(")

    for i, line in enumerate(lines):
        if target_pattern.search(line):
            start_idx = i
            while start_idx > 0 and lines[start_idx - 1].strip().startswith(("@", "/*", "*", "//")):
                start_idx -= 1

            depth = 0
            found_open = False
            end_idx = i

            for j in range(i, len(lines)):
                ln = lines[j]
                clean = re.sub(r'"(\\.|[^"\\])*"', '""', ln)
                clean = re.sub(r"'(\\.|[^'\\])*'", "''", clean)
                clean = re.sub(r"//.*$", "", clean)

                for ch in clean:
                    if ch == "{":
                        depth += 1
                        found_open = True
                    elif ch == "}":
                        depth -= 1
                        if found_open and depth == 0:
                            end_idx = j
                            return "\n".join(lines[start_idx : end_idx + 1])
                if not found_open and ";" in clean and depth == 0:
                    return "\n".join(lines[start_idx : j + 1])
    return None


# -----------------------------------------------------------------------------
# Bytecode Disassembler via javap
# -----------------------------------------------------------------------------

def run_javap(class_name: str, index: JarIndex, verbose: bool = False, bytecode: bool = False) -> str:
    resolved = index.resolve_class(class_name)
    cp = index.get_classpath()

    cmd = ["javap"]
    if cp:
        cmd.extend(["-cp", cp])
    if not bytecode and not verbose:
        cmd.append("-p")
    if bytecode:
        cmd.append("-c")
    if verbose:
        cmd.append("-v")
    cmd.append(resolved)

    try:
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
        if proc.returncode == 0:
            return proc.stdout
        return f"javap error (exit {proc.returncode}):\n{proc.stderr.strip()}"
    except FileNotFoundError:
        return "Error: 'javap' command not found in PATH. Ensure JDK 21 is installed and available."


def parse_class_members(javap_output: str) -> Dict[str, Any]:
    lines = javap_output.splitlines()
    res: Dict[str, Any] = {"constructors": [], "methods": [], "fields": [], "header": ""}
    for line in lines:
        line_s = line.strip()
        if not line_s or line_s.startswith(("//", "Compiled from", "}", "{", "static {};")):
            continue
        if any(line_s.startswith(p) for p in ("public class", "public interface", "public enum", "public record", "class ", "interface ")):
            res["header"] = line_s
            continue
        if "(" in line_s and ")" in line_s:
            if ";" in line_s:
                if line_s.split("(")[0].strip().split()[-1].endswith("."):
                    res["constructors"].append(line_s)
                else:
                    res["methods"].append(line_s)
        elif ";" in line_s:
            res["fields"].append(line_s)
    return res


# -----------------------------------------------------------------------------
# Inspection Commands: Events, Capabilities, Registries, Components, Gameplay
# -----------------------------------------------------------------------------

def inspect_events(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, Any]]:
    if not index.sources_jar or not index.sources_jar.exists():
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    results = []

    try:
        with zipfile.ZipFile(index.sources_jar, "r") as z:
            for entry in z.namelist():
                if not entry.endswith(".java") or "package-info" in entry:
                    continue
                if not ("event" in entry.lower() and "Event" in entry):
                    continue

                cls_name = entry[:-5].replace("/", ".")
                if pattern and not pattern.search(cls_name):
                    continue

                try:
                    with z.open(entry) as f:
                        src = f.read().decode("utf-8", errors="ignore")
                except Exception:
                    continue

                if "class " not in src:
                    continue

                is_cancelable = "@Cancelable" in src or "ICancellableEvent" in src
                has_result = "@Event.HasResult" in src or "@HasResult" in src
                getters = re.findall(r"public\s+([A-Za-z0-9_<>\[\]]+)\s+(get[A-Z][A-Za-z0-9_]*)\s*\(\)", src)

                results.append({
                    "class": cls_name,
                    "cancelable": is_cancelable,
                    "has_result": has_result,
                    "getters": [f"{name}(): {ret}" for ret, name in getters[:12]]
                })
    except (zipfile.BadZipFile, OSError):
        pass

    results.sort(key=lambda x: x["class"])
    return results


def inspect_capabilities(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, Any]]:
    """
    Multi-platform Capability Inspection:
    - NeoForge: Capabilities.ItemHandler, EnergyStorage, FluidHandler
    - Fabric: Fabric Transfer API
    - Forge: CapabilityManager
    """
    results = []
    pattern = re.compile(query, re.IGNORECASE) if query else None

    # 1. NeoForge Capabilities
    src = index.get_source_raw("net/neoforged/neoforge/capabilities/Capabilities.java")
    if src:
        inner_classes = re.findall(r"public\s+static\s+final\s+class\s+([A-Za-z0-9_]+)\s*\{([^}]+)\}", src)
        for class_name, body in inner_classes:
            caps = re.findall(
                r"public\s+static\s+final\s+([A-Za-z0-9_<>@\s,]+)\s+([A-Z0-9_]+)\s*=\s*([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)\(",
                body
            )
            for cap_type, cap_field, factory_cls, factory_method in caps:
                full_name = f"Capabilities.{class_name}.{cap_field}"
                clean_type = re.sub(r"\s+", " ", cap_type).strip()
                if pattern and not (pattern.search(full_name) or pattern.search(class_name) or pattern.search(clean_type)):
                    continue
                results.append({
                    "platform": "NeoForge",
                    "capability": full_name,
                    "domain": class_name,
                    "field": cap_field,
                    "type": clean_type,
                    "factory": f"{factory_cls}.{factory_method}"
                })

    # 2. Fabric Transfer API (if on Fabric)
    if not results and index.active_platform_type == "Fabric":
        for entry in index._source_namelist:
            if "fabric/api/transfer/v1" in entry and entry.endswith(".java"):
                cls_name = entry[:-5].replace("/", ".")
                simple = cls_name.split(".")[-1]
                if pattern and not pattern.search(cls_name):
                    continue
                results.append({
                    "platform": "Fabric",
                    "capability": simple,
                    "domain": "TransferAPI",
                    "field": simple,
                    "type": cls_name,
                    "factory": "FabricTransfer"
                })

    return results


def inspect_extensions(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, Any]]:
    if not index.sources_jar or not index.sources_jar.exists():
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    results = []

    try:
        with zipfile.ZipFile(index.sources_jar, "r") as z:
            for entry in z.namelist():
                if not entry.endswith(".java") or "package-info" in entry:
                    continue
                if "extensions" not in entry.lower():
                    continue

                cls_name = entry[:-5].replace("/", ".")
                if pattern and not pattern.search(cls_name):
                    continue

                try:
                    with z.open(entry) as f:
                        src = f.read().decode("utf-8", errors="ignore")
                except Exception:
                    continue

                methods = re.findall(r"default\s+([A-Za-z0-9_<>\[\],\s]+)\s+([A-Za-z0-9_]+)\s*\(([^)]*)\)", src)

                results.append({
                    "interface": cls_name,
                    "methods": [f"{name}({params.strip()}): {ret.strip()}" for ret, name, params in methods[:15]]
                })
    except (zipfile.BadZipFile, OSError):
        pass

    results.sort(key=lambda x: x["interface"])
    return results


def inspect_registries(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, str]]:
    src = index.get_source_raw("net/minecraft/core/registries/BuiltInRegistries.java")
    if not src:
        src = index.get_source_raw("net/minecraft/core/Registry.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    matches = re.findall(r"Registry<([^>]+)>\s+([A-Z_]+)\s*=\s*registerSimple\(\s*Registries\.([A-Z_]+)", src)
    if not matches:
        matches = re.findall(r"Registry<([^>]+)>\s+([A-Z_]+)\s*=", src)
        matches = [(m[0], m[1], m[1]) for m in matches]

    results = []
    for elem_type, field_name, reg_key in matches:
        if pattern and not (pattern.search(field_name) or pattern.search(elem_type)):
            continue
        results.append({
            "registry": field_name,
            "type": elem_type,
            "key": f"Registries.{reg_key}"
        })
    return results


def inspect_tags(index: JarIndex, category: str = "item") -> List[Tuple[str, str]]:
    cat_lower = category.lower()
    mapping = {
        "item": "net/minecraft/tags/ItemTags.java",
        "block": "net/minecraft/tags/BlockTags.java",
        "enchantment": "net/minecraft/tags/EnchantmentTags.java",
        "entity": "net/minecraft/tags/EntityTypeTags.java",
        "biome": "net/minecraft/tags/BiomeTags.java",
        "fluid": "net/minecraft/tags/FluidTags.java"
    }
    target_file = mapping.get(cat_lower)
    if not target_file:
        for k, v in mapping.items():
            if k in cat_lower:
                target_file = v
                break
    if not target_file:
        target_file = mapping["item"]

    src = index.get_source_raw(target_file)
    if not src:
        return []

    tags = re.findall(r"TagKey<[^>]+>\s+([A-Z_]+)\s*=\s*(?:bind|create)\(\"([^\"]+)\"\)", src)
    return [(name, f"#{tag_id}") for name, tag_id in tags]


def inspect_components(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, str]]:
    """Extracts Minecraft DataComponents (1.20.5+) constants and their value types."""
    src = index.get_source_raw("net/minecraft/core/component/DataComponents.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    matches = re.findall(r"public\s+static\s+final\s+DataComponentType<([^>]+)>\s+([A-Z_]+)\s*=\s*register\(\s*\"([^\"]+)\"", src)
    results = []
    for val_type, field_name, comp_id in matches:
        if pattern and not (pattern.search(field_name) or pattern.search(comp_id) or pattern.search(val_type)):
            continue
        results.append({
            "constant": field_name,
            "id": f"minecraft:{comp_id}",
            "type": val_type.strip()
        })
    return results


def inspect_attributes(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, str]]:
    """Extracts Minecraft Attributes constants and their ranges."""
    src = index.get_source_raw("net/minecraft/world/entity/ai/attributes/Attributes.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    matches = re.findall(r"Holder<Attribute>\s+([A-Z_]+)\s*=\s*register\(\s*\"([^\"]+)\",\s*new\s+RangedAttribute\(\"[^\"]+\",\s*([^,]+),\s*([^,]+),\s*([^)]+)\)", src)
    results = []
    for field_name, attr_id, default_val, min_val, max_val in matches:
        if pattern and not (pattern.search(field_name) or pattern.search(attr_id)):
            continue
        results.append({
            "name": field_name,
            "id": f"minecraft:{attr_id}",
            "default": default_val.strip(),
            "range": f"[{min_val.strip()} .. {max_val.strip()}]"
        })
    return results


def inspect_properties(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, str]]:
    """Extracts BlockStateProperties constants."""
    src = index.get_source_raw("net/minecraft/world/level/block/state/properties/BlockStateProperties.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    matches = re.findall(r"public\s+static\s+final\s+([A-Za-z0-9_<>]+)\s+([A-Z0-9_]+)\s*=\s*([A-Za-z]+)\.create\(\"([^\"]+)\"", src)
    results = []
    for prop_type, field_name, factory, prop_id in matches:
        if pattern and not (pattern.search(field_name) or pattern.search(prop_id)):
            continue
        results.append({
            "property": field_name,
            "id": prop_id,
            "type": prop_type.strip()
        })
    return results


def inspect_packets(index: JarIndex, direction_or_query: Optional[str] = None) -> List[Dict[str, str]]:
    """Lists Minecraft network protocol packets."""
    if not index.sources_jar or not index.sources_jar.exists():
        return []

    results = []
    target_dir = "net/minecraft/network/protocol/game"
    pattern = re.compile(direction_or_query, re.IGNORECASE) if direction_or_query else None

    try:
        with zipfile.ZipFile(index.sources_jar, "r") as z:
            for entry in z.namelist():
                if entry.startswith(target_dir) and entry.endswith("Packet.java"):
                    name = entry.split("/")[-1][:-5]
                    direction = "CLIENTBOUND" if name.startswith("Clientbound") else ("SERVERBOUND" if name.startswith("Serverbound") else "COMMON")
                    if pattern and not (pattern.search(name) or pattern.search(direction)):
                        continue
                    cls_name = entry[:-5].replace("/", ".")
                    results.append({"name": name, "direction": direction, "class": cls_name})
    except (zipfile.BadZipFile, OSError):
        pass

    results.sort(key=lambda x: (x["direction"], x["name"]))
    return results


def inspect_effects(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, str]]:
    """Extracts Minecraft MobEffects constants, registry IDs, categories, and colors."""
    src = index.get_source_raw("net/minecraft/world/effect/MobEffects.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    results = []

    entries = re.findall(
        r"public\s+static\s+final\s+Holder<MobEffect>\s+([A-Z0-9_]+)\s*=\s*register\(\s*\"([^\"]+)\"(.*?);",
        src,
        re.DOTALL
    )
    for const_name, effect_id, body in entries:
        category = "BENEFICIAL"
        m_cat = re.search(r"MobEffectCategory\.(BENEFICIAL|HARMFUL|NEUTRAL)", body)
        if m_cat:
            category = m_cat.group(1)

        color_str = ""
        m_col = re.search(r",\s*([0-9]{5,8})\b", body)
        if m_col:
            val = int(m_col.group(1))
            color_str = f"#{val:06x}"

        if pattern and not (pattern.search(const_name) or pattern.search(effect_id) or pattern.search(category)):
            continue

        results.append({
            "constant": const_name,
            "id": f"minecraft:{effect_id}",
            "category": category,
            "color": color_str
        })
    return results


def inspect_potions(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, str]]:
    """Extracts Minecraft Potions constants and registry IDs."""
    src = index.get_source_raw("net/minecraft/world/item/alchemy/Potions.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    results = []

    matches = re.findall(r"public\s+static\s+final\s+Holder<Potion>\s+([A-Z0-9_]+)\s*=\s*register\(\s*\"([^\"]+)\"", src)
    for const_name, potion_id in matches:
        if pattern and not (pattern.search(const_name) or pattern.search(potion_id)):
            continue
        results.append({
            "constant": const_name,
            "id": f"minecraft:{potion_id}"
        })
    return results


def inspect_sounds(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, str]]:
    """Extracts Minecraft SoundEvents constants and sound identifiers."""
    src = index.get_source_raw("net/minecraft/sounds/SoundEvents.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    results = []

    matches = re.findall(
        r"public\s+static\s+final\s+(?:Holder\.Reference<SoundEvent>|SoundEvent)\s+([A-Z0-9_]+)\s*=\s*(?:register|registerForHolder)\(\"([^\"]+)\"\);",
        src
    )
    for const_name, sound_id in matches:
        if pattern and not (pattern.search(const_name) or pattern.search(sound_id)):
            continue
        results.append({
            "constant": const_name,
            "id": f"minecraft:{sound_id}"
        })
    return results


def inspect_particles(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, Any]]:
    """Extracts Minecraft ParticleTypes constants, IDs, and classes."""
    src = index.get_source_raw("net/minecraft/core/particles/ParticleTypes.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    results = []

    matches = re.findall(
        r"public\s+static\s+final\s+(ParticleType<[^>]+>|SimpleParticleType)\s+([A-Z0-9_]+)\s*=\s*register\(\s*\"([^\"]+)\",\s*(true|false)",
        src
    )
    for p_type, const_name, part_id, override in matches:
        if pattern and not (pattern.search(const_name) or pattern.search(part_id) or pattern.search(p_type)):
            continue
        results.append({
            "constant": const_name,
            "id": f"minecraft:{part_id}",
            "type": p_type.strip(),
            "always_render": override == "true"
        })
    return results


def inspect_entities(index: JarIndex, query: Optional[str] = None) -> List[Dict[str, str]]:
    """Extracts Minecraft EntityType constants, entity classes, and category."""
    src = index.get_source_raw("net/minecraft/world/entity/EntityType.java")
    if not src:
        return []

    pattern = re.compile(query, re.IGNORECASE) if query else None
    results = []

    entries = re.findall(
        r"public\s+static\s+final\s+EntityType<([^>]+)>\s+([A-Z0-9_]+)\s*=\s*register\(\s*\"([^\"]+)\"(.*?);",
        src,
        re.DOTALL
    )
    for ent_cls, const_name, ent_id, body in entries:
        category = "MISC"
        m_cat = re.search(r"MobCategory\.([A-Z_]+)", body)
        if m_cat:
            category = m_cat.group(1)

        if pattern and not (pattern.search(const_name) or pattern.search(ent_id) or pattern.search(ent_cls)):
            continue

        results.append({
            "constant": const_name,
            "id": f"minecraft:{ent_id}",
            "class": ent_cls.strip(),
            "category": category
        })
    return results


def build_class_tree(class_name: str, index: JarIndex) -> Dict[str, Any]:
    resolved = index.resolve_class(class_name)
    raw = run_javap(resolved, index, verbose=False, bytecode=False)
    members = parse_class_members(raw)
    header = members.get("header", "")

    super_class = None
    interfaces = []

    m_extends = re.search(r"extends\s+([A-Za-z0-9_.$]+)", header)
    if m_extends:
        super_class = m_extends.group(1)

    m_implements = re.search(r"implements\s+([^{]+)", header)
    if m_implements:
        interfaces = [i.strip() for i in m_implements.group(1).split(",")]

    return {
        "class": resolved,
        "header": header,
        "super": super_class,
        "interfaces": interfaces
    }


def search_usages(symbol: str, index: JarIndex, limit: int = 30) -> List[Tuple[str, int, str]]:
    if not index.sources_jar or not index.sources_jar.exists():
        return []

    results = []
    pattern = re.compile(r"\b" + re.escape(symbol) + r"\b")

    try:
        with zipfile.ZipFile(index.sources_jar, "r") as z:
            for entry in z.namelist():
                if not entry.endswith(".java"):
                    continue
                try:
                    with z.open(entry) as f:
                        content = f.read().decode("utf-8", errors="ignore")
                        if pattern.search(content):
                            cls_name = entry[:-5].replace("/", ".")
                            for line_num, line in enumerate(content.splitlines(), 1):
                                if pattern.search(line):
                                    results.append((cls_name, line_num, line.strip()))
                                    if len(results) >= limit:
                                        return results
                except Exception:
                    continue
    except (zipfile.BadZipFile, OSError):
        pass
    return results


# -----------------------------------------------------------------------------
# Interactive REPL Shell & Command Dispatcher
# -----------------------------------------------------------------------------

def run_repl(index: JarIndex):
    print(f"{BOLD}{CYAN}=== Minecraft & Multi-Platform Inspection REPL ==={RESET}")
    print(f"{DIM}Platform: {YELLOW}{index.active_platform_type}{RESET} {DIM}(MC {index.active_mc_version}){RESET}")
    print(f"{DIM}Commands: search, source, disasm, info, tree, methods, fields, events, capabilities,")
    print(f"          effects, potions, sounds, particles, entities, components, attributes, properties,")
    print(f"          packets, tags, registries, usages, status, classpath, exit{RESET}\n")

    while True:
        try:
            line = input(f"{BOLD}{GREEN}mc>{RESET} ").strip()
            if not line:
                continue
            if line in ("exit", "quit", "q"):
                print("Bye!")
                break
            if line in ("help", "?"):
                print("Available commands:")
                print("  search <query>          - Find classes by regex / name")
                print("  source <class> [-m fn]  - View Java source code from sources.jar")
                print("  disasm <class> [-c]     - Disassemble bytecode with javap")
                print("  info <class>            - Class summary & member counts")
                print("  tree <class>            - Class inheritance tree")
                print("  methods <class> [-q flt]- List declared methods")
                print("  fields <class> [-q flt] - List declared fields & constants")
                print("  effects [query]         - Vanilla MobEffects (speed, slowness...)")
                print("  potions [query]         - Vanilla Potions (healing, swiftness...)")
                print("  sounds [query]          - Vanilla SoundEvents (1580+ sound IDs)")
                print("  particles [query]       - Vanilla ParticleTypes (flame, crit...)")
                print("  entities [query]        - Vanilla EntityTypes (Zombie, Allay...)")
                print("  capabilities [query]    - Platform Capabilities (NeoForge/Fabric)")
                print("  events [query]          - Platform Events & cancelable flags")
                print("  extensions [query]      - Platform Extension interfaces")
                print("  components [query]      - Minecraft DataComponents (1.20.5+)")
                print("  attributes [query]      - Minecraft Entity Attributes")
                print("  properties [query]      - BlockStateProperties")
                print("  packets [query]         - Clientbound & Serverbound packets")
                print("  tags [category]         - TagKey collections (item, block, enchant...)")
                print("  registries [query]      - BuiltInRegistries")
                print("  usages <symbol>         - Source code references across Minecraft")
                print("  status                  - Active platform and environment status")
                print("  classpath               - View resolved classpath JARs")
                print("  exit                    - Exit REPL session")
                continue

            args = shlex.split(line)
            dispatch_command(args, index)
        except (KeyboardInterrupt, EOFError):
            print("\nExiting REPL.")
            break
        except Exception as e:
            print(f"{RED}Error: {e}{RESET}")


def dispatch_command(cmd_args: List[str], index: JarIndex):
    parser = build_parser()
    try:
        args = parser.parse_args(cmd_args)
    except SystemExit:
        if cmd_args and not cmd_args[0].startswith("-"):
            sugg = suggest_command(cmd_args[0])
            if sugg:
                print(f"{YELLOW}Unknown command '{cmd_args[0]}'. Did you mean: '{sugg}'?{RESET}")
        return

    cmd = args.command
    if not cmd:
        parser.print_help()
        return

    if cmd in ("status", "platform"):
        status_info = {
            "platform": index.active_platform_type,
            "platform_version": index.active_platform_version,
            "minecraft_version": index.active_mc_version,
            "sources_jar": str(index.sources_jar) if index.sources_jar else None,
            "merged_jar": str(index.merged_jar) if index.merged_jar else None,
            "discovered_platforms": [p["name"] for p in index.discovered_platforms],
            "classpath_count": len(index.all_jars)
        }
        if args.json:
            print(json.dumps(status_info, indent=2))
        else:
            print(f"{BOLD}=== Inspection Environment Status ==={RESET}")
            print(f"  Active Platform:    {CYAN}{index.active_platform_type}{RESET} (Version: {YELLOW}{index.active_platform_version}{RESET})")
            print(f"  Minecraft Version:  {GREEN}{index.active_mc_version}{RESET}")
            print(f"  Sources JAR:        {index.sources_jar or RED + 'None' + RESET}")
            print(f"  Merged/Mapped JAR:  {index.merged_jar or RED + 'None' + RESET}")
            print(f"  Discovered Modules: {', '.join(p['name'] for p in index.discovered_platforms) if index.discovered_platforms else 'None'}")
            print(f"  Classpath JARs:     {len(index.all_jars)} total")

    elif cmd in ("search", "find", "-f"):
        matches = index.find_classes(args.query, args.limit)
        if args.json:
            print(json.dumps([{"class": c, "jar": j} for c, j in matches], indent=2))
        else:
            print(f"{BOLD}Found {len(matches)} class(es) matching '{args.query}':{RESET}")
            for c, j in matches:
                print(f"  {CYAN}{c}{RESET} {DIM}({j}){RESET}")

    elif cmd in ("source", "-s"):
        resolved = index.resolve_class(args.class_name)
        src = index.get_source(resolved)
        if not src:
            print(f"{RED}Error: Source not found for '{args.class_name}' ({resolved}) in sources.jar.{RESET}")
            return

        if args.method:
            method_src = extract_method_source(src, args.method)
            if not method_src:
                print(f"{RED}Error: Method '{args.method}' not found in source for '{resolved}'.{RESET}")
                return
            src = method_src
        elif args.lines:
            parts = args.lines.split(":")
            if len(parts) == 2 and parts[0].isdigit() and parts[1].isdigit():
                start, end = int(parts[0]), int(parts[1])
                lines = src.splitlines()[max(0, start - 1):end]
                src = "\n".join(lines)

        if args.json:
            print(json.dumps({"class": resolved, "source": src}, indent=2))
        else:
            print(highlight_java(src))

    elif cmd in ("disasm", "javap", "-d"):
        resolved = index.resolve_class(args.class_name)
        raw = run_javap(resolved, index, verbose=args.verbose, bytecode=args.code)
        if args.method and "error" not in raw.lower():
            filtered = []
            capturing = False
            for line in raw.splitlines():
                if re.search(r"\b" + re.escape(args.method) + r"\b", line):
                    capturing = True
                elif capturing and any(line.startswith(p) for p in ("  public ", "  protected ", "  private ", "}")):
                    if not line.strip().startswith("Code:"):
                        capturing = False
                if capturing:
                    filtered.append(line)
            out = "\n".join(filtered) if filtered else f"Method '{args.method}' not found in javap output."
        else:
            out = raw

        if args.json:
            print(json.dumps({"class": resolved, "output": out}, indent=2))
        else:
            print(out)

    elif cmd == "info":
        resolved = index.resolve_class(args.class_name)
        raw = run_javap(resolved, index)
        members = parse_class_members(raw)
        has_src = index.get_source(resolved) is not None
        info_data = {
            "class": resolved,
            "header": members.get("header", ""),
            "has_source": has_src,
            "fields_count": len(members["fields"]),
            "constructors_count": len(members["constructors"]),
            "methods_count": len(members["methods"])
        }
        if args.json:
            print(json.dumps(info_data, indent=2))
        else:
            print(f"{BOLD}=== Class: {CYAN}{resolved}{RESET} ===")
            if members.get("header"):
                print(f"Declaration: {members['header']}")
            print(f"Source in sources.jar: {GREEN + 'YES' if has_src else RED + 'NO'}{RESET}")
            print(f"Fields: {len(members['fields'])} | Constructors: {len(members['constructors'])} | Methods: {len(members['methods'])}")

    elif cmd == "tree":
        resolved = index.resolve_class(args.class_name)
        tree_info = build_class_tree(resolved, index)
        if args.json:
            print(json.dumps(tree_info, indent=2))
        else:
            print(f"{BOLD}=== Hierarchy Tree for {CYAN}{resolved}{RESET} ===")
            print(f"Header: {tree_info['header']}")
            if tree_info['super']:
                print(f"  {BOLD}Extends:{RESET} {YELLOW}{tree_info['super']}{RESET}")
            if tree_info['interfaces']:
                print(f"  {BOLD}Implements:{RESET}")
                for i in tree_info['interfaces']:
                    print(f"    - {GREEN}{i}{RESET}")

    elif cmd in ("methods", "list-methods"):
        resolved = index.resolve_class(args.class_name)
        raw = run_javap(resolved, index)
        members = parse_class_members(raw)
        methods = members["methods"]
        if args.filter:
            flt = args.filter.lower()
            methods = [m for m in methods if flt in m.lower()]
        if args.json:
            print(json.dumps({"class": resolved, "methods": methods}, indent=2))
        else:
            print(f"{BOLD}Methods in {CYAN}{resolved}{RESET} ({len(methods)} found):")
            for m in methods:
                print(f"  {m}")

    elif cmd == "fields":
        resolved = index.resolve_class(args.class_name)
        raw = run_javap(resolved, index)
        members = parse_class_members(raw)
        fields = members["fields"]
        if args.filter:
            flt = args.filter.lower()
            fields = [f for f in fields if flt in f.lower()]
        if args.json:
            print(json.dumps({"class": resolved, "fields": fields}, indent=2))
        else:
            print(f"{BOLD}Fields in {CYAN}{resolved}{RESET} ({len(fields)} found):")
            for f in fields:
                print(f"  {f}")

    elif cmd == "events":
        evs = inspect_events(index, args.query)
        if args.json:
            print(json.dumps(evs, indent=2))
        else:
            print(f"{BOLD}Found {len(evs)} Platform Event(s):{RESET}")
            for e in evs:
                badge = f"{GREEN}[CANCELABLE]{RESET}" if e["cancelable"] else f"{DIM}[NON-CANCELABLE]{RESET}"
                res_badge = f" {YELLOW}[HAS-RESULT]{RESET}" if e["has_result"] else ""
                print(f"\n* {BOLD}{CYAN}{e['class']}{RESET} {badge}{res_badge}")
                if e["getters"]:
                    print(f"  {DIM}Getters:{RESET} " + ", ".join(e["getters"][:6]))

    elif cmd in ("capabilities", "caps"):
        caps = inspect_capabilities(index, args.query)
        if args.json:
            print(json.dumps(caps, indent=2))
        else:
            print(f"{BOLD}Found {len(caps)} Capability Definition(s):{RESET}")
            for c in caps:
                print(f"  {CYAN}{c['capability']:<36}{RESET} [{YELLOW}{c['platform']}{RESET}] -> {GREEN}{c['type']}{RESET}")

    elif cmd == "extensions":
        exts = inspect_extensions(index, args.query)
        if args.json:
            print(json.dumps(exts, indent=2))
        else:
            print(f"{BOLD}Found {len(exts)} Platform Extension Interface(s):{RESET}")
            for x in exts:
                print(f"\n* {BOLD}{CYAN}{x['interface']}{RESET}")
                if x["methods"]:
                    for m in x["methods"][:6]:
                        print(f"    {DIM}default{RESET} {m}")

    elif cmd == "registries":
        regs = inspect_registries(index, args.query)
        if args.json:
            print(json.dumps(regs, indent=2))
        else:
            print(f"{BOLD}Found {len(regs)} BuiltInRegistries:{RESET}")
            for r in regs:
                print(f"  {CYAN}{r['registry']:<26}{RESET} -> {r['type']} {DIM}({r['key']}){RESET}")

    elif cmd == "tags":
        tags = inspect_tags(index, args.category)
        if args.json:
            print(json.dumps([{"name": n, "tag": t} for n, t in tags], indent=2))
        else:
            print(f"{BOLD}Built-in {args.category.capitalize()}Tags ({len(tags)} found):{RESET}")
            for name, tag_id in tags:
                print(f"  {CYAN}{name:<32}{RESET} -> {GREEN}{tag_id}{RESET}")

    elif cmd == "components":
        comps = inspect_components(index, args.query)
        if args.json:
            print(json.dumps(comps, indent=2))
        else:
            print(f"{BOLD}Minecraft DataComponents ({len(comps)} found):{RESET}")
            for c in comps:
                print(f"  {CYAN}{c['constant']:<26}{RESET} -> {GREEN}{c['id']:<32}{RESET} {DIM}({c['type']}){RESET}")

    elif cmd == "attributes":
        attrs = inspect_attributes(index, args.query)
        if args.json:
            print(json.dumps(attrs, indent=2))
        else:
            print(f"{BOLD}Minecraft Attributes ({len(attrs)} found):{RESET}")
            for a in attrs:
                print(f"  {CYAN}{a['name']:<28}{RESET} -> {GREEN}{a['id']:<36}{RESET} default={YELLOW}{a['default']:<5}{RESET} {DIM}{a['range']}{RESET}")

    elif cmd == "properties":
        props = inspect_properties(index, args.query)
        if args.json:
            print(json.dumps(props, indent=2))
        else:
            print(f"{BOLD}BlockStateProperties ({len(props)} found):{RESET}")
            for p in props:
                print(f"  {CYAN}{p['property']:<28}{RESET} -> {GREEN}{p['id']:<24}{RESET} {DIM}({p['type']}){RESET}")

    elif cmd == "packets":
        pkts = inspect_packets(index, args.query)
        if args.json:
            print(json.dumps(pkts, indent=2))
        else:
            print(f"{BOLD}Game Protocol Packets ({len(pkts)} found):{RESET}")
            for p in pkts:
                badge = f"{CYAN}[CLIENT]{RESET}" if p["direction"] == "CLIENTBOUND" else f"{YELLOW}[SERVER]{RESET}"
                print(f"  {badge} {p['name']:<38} {DIM}({p['class']}){RESET}")

    elif cmd == "effects":
        effs = inspect_effects(index, args.query)
        if args.json:
            print(json.dumps(effs, indent=2))
        else:
            print(f"{BOLD}Minecraft MobEffects ({len(effs)} found):{RESET}")
            for e in effs:
                badge = f"{GREEN}[BENEFICIAL]{RESET}" if e["category"] == "BENEFICIAL" else (f"{RED}[HARMFUL]{RESET}" if e["category"] == "HARMFUL" else f"{YELLOW}[NEUTRAL]{RESET}")
                col = f" color={e['color']}" if e["color"] else ""
                print(f"  {CYAN}{e['constant']:<24}{RESET} -> {GREEN}{e['id']:<30}{RESET} {badge}{DIM}{col}{RESET}")

    elif cmd == "potions":
        pots = inspect_potions(index, args.query)
        if args.json:
            print(json.dumps(pots, indent=2))
        else:
            print(f"{BOLD}Minecraft Potions ({len(pots)} found):{RESET}")
            for p in pots:
                print(f"  {CYAN}{p['constant']:<24}{RESET} -> {GREEN}{p['id']}{RESET}")

    elif cmd == "sounds":
        snds = inspect_sounds(index, args.query)
        if args.json:
            print(json.dumps(snds, indent=2))
        else:
            print(f"{BOLD}Minecraft SoundEvents ({len(snds)} found):{RESET}")
            for s in snds:
                print(f"  {CYAN}{s['constant']:<36}{RESET} -> {GREEN}{s['id']}{RESET}")

    elif cmd == "particles":
        parts = inspect_particles(index, args.query)
        if args.json:
            print(json.dumps(parts, indent=2))
        else:
            print(f"{BOLD}Minecraft ParticleTypes ({len(parts)} found):{RESET}")
            for p in parts:
                print(f"  {CYAN}{p['constant']:<28}{RESET} -> {GREEN}{p['id']:<30}{RESET} {DIM}({p['type']}){RESET}")

    elif cmd == "entities":
        ents = inspect_entities(index, args.query)
        if args.json:
            print(json.dumps(ents, indent=2))
        else:
            print(f"{BOLD}Minecraft EntityTypes ({len(ents)} found):{RESET}")
            for e in ents:
                print(f"  {CYAN}{e['constant']:<26}{RESET} -> {GREEN}{e['id']:<26}{RESET} {YELLOW}{e['class']:<20}{RESET} {DIM}[{e['category']}]{RESET}")

    elif cmd in ("usages", "references", "refs"):
        usages = search_usages(args.symbol, index, args.limit)
        if args.json:
            print(json.dumps([{"class": c, "line": n, "code": s} for c, n, s in usages], indent=2))
        else:
            print(f"{BOLD}Found {len(usages)} reference(s) to '{args.symbol}':{RESET}")
            for cls, line_num, snippet in usages:
                print(f"  {CYAN}{cls}{RESET}:{YELLOW}{line_num}{RESET} -> {snippet}")

    elif cmd == "classpath":
        cp = index.get_classpath()
        if args.json:
            print(json.dumps({"classpath": cp, "jars": [str(j) for j in index.all_jars]}, indent=2))
        else:
            print(f"{BOLD}Discovered Classpath Jars ({len(index.all_jars)}):{RESET}")
            for j in index.all_jars:
                print(f"  {j}")
            print(f"\n{BOLD}Full Classpath String:{RESET}\n{cp}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="mc_inspect - Multi-Platform Minecraft & Modding Toolkit & Class Inspector",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    # Global multi-platform options
    parser.add_argument("--platform", help="Target platform (neoforge, fabric, forge, vanilla)")
    parser.add_argument("--sources", help="Path to custom sources.jar")
    parser.add_argument("--jar", help="Path to custom game/merged.jar")
    parser.add_argument("--json", action="store_true", help="Output results in JSON format")

    subparsers = parser.add_subparsers(dest="command", help="Command to run")

    # status / platform
    subparsers.add_parser("status", aliases=["platform"], help="Display environment, platform, and Minecraft version")

    # shell
    subparsers.add_parser("shell", aliases=["repl", "-i"], help="Launch interactive REPL session")

    # search
    p_search = subparsers.add_parser("search", aliases=["find", "-f"], help="Search classes across all jars")
    p_search.add_argument("query", help="Class substring or regex")
    p_search.add_argument("--limit", type=int, default=50)

    # source
    p_source = subparsers.add_parser("source", aliases=["-s"], help="View Java source from sources.jar")
    p_source.add_argument("class_name", help="Fully qualified or short class name (e.g. MobEffect, Items, Zombie)")
    p_source.add_argument("-m", "--method", help="Extract only this method")
    p_source.add_argument("-l", "--lines", help="Line range e.g. 1:40")

    # disasm
    p_disasm = subparsers.add_parser("disasm", aliases=["javap", "-d"], help="Disassemble bytecode via javap")
    p_disasm.add_argument("class_name", help="Class name (e.g. MobEffect, Level)")
    p_disasm.add_argument("-c", "--code", action="store_true", help="Print bytecode instructions")
    p_disasm.add_argument("-v", "--verbose", action="store_true", help="Verbose javap output")
    p_disasm.add_argument("-m", "--method", help="Filter javap output to this method")

    # info
    p_info = subparsers.add_parser("info", help="Summary overview of a class")
    p_info.add_argument("class_name", help="Class name")

    # tree
    p_tree = subparsers.add_parser("tree", help="View inheritance hierarchy tree")
    p_tree.add_argument("class_name", help="Class name")

    # methods
    p_methods = subparsers.add_parser("methods", aliases=["list-methods"], help="List declared methods")
    p_methods.add_argument("class_name", help="Class name")
    p_methods.add_argument("-q", "--filter", help="Method name filter")

    # fields
    p_fields = subparsers.add_parser("fields", help="List declared fields and constants")
    p_fields.add_argument("class_name", help="Class name")
    p_fields.add_argument("-q", "--filter", help="Field name filter")

    # effects
    p_effs = subparsers.add_parser("effects", help="Browse Minecraft MobEffects (speed, slowness...)")
    p_effs.add_argument("query", nargs="?", default="", help="Optional effect filter")

    # potions
    p_pots = subparsers.add_parser("potions", help="Browse Minecraft Potions (healing, swiftness...)")
    p_pots.add_argument("query", nargs="?", default="", help="Optional potion filter")

    # sounds
    p_snds = subparsers.add_parser("sounds", help="Browse Minecraft SoundEvents (1580+ sounds)")
    p_snds.add_argument("query", nargs="?", default="", help="Optional sound filter")

    # particles
    p_parts = subparsers.add_parser("particles", help="Browse Minecraft ParticleTypes (flame, smoke...)")
    p_parts.add_argument("query", nargs="?", default="", help="Optional particle filter")

    # entities
    p_ents = subparsers.add_parser("entities", help="Browse Minecraft EntityTypes (Zombie, Skeleton...)")
    p_ents.add_argument("query", nargs="?", default="", help="Optional entity filter")

    # capabilities
    p_caps = subparsers.add_parser("capabilities", aliases=["caps"], help="Browse platform capabilities (NeoForge/Fabric)")
    p_caps.add_argument("query", nargs="?", default="", help="Optional capability filter")

    # events
    p_events = subparsers.add_parser("events", help="Browse platform events and cancelable flags")
    p_events.add_argument("query", nargs="?", default="", help="Optional event filter")

    # extensions
    p_exts = subparsers.add_parser("extensions", help="Browse platform extension interfaces")
    p_exts.add_argument("query", nargs="?", default="", help="Optional extension filter")

    # registries
    p_regs = subparsers.add_parser("registries", help="Browse Minecraft BuiltInRegistries")
    p_regs.add_argument("query", nargs="?", default="", help="Optional registry filter")

    # tags
    p_tags = subparsers.add_parser("tags", help="Browse Minecraft TagKeys")
    p_tags.add_argument("category", nargs="?", default="item", help="Category: item, block, enchantment, entity, biome, fluid")

    # components
    p_comps = subparsers.add_parser("components", help="Browse Minecraft DataComponents (1.20.5+)")
    p_comps.add_argument("query", nargs="?", default="", help="Optional component filter")

    # attributes
    p_attrs = subparsers.add_parser("attributes", help="Browse Minecraft Entity Attributes")
    p_attrs.add_argument("query", nargs="?", default="", help="Optional attribute filter")

    # properties
    p_props = subparsers.add_parser("properties", help="Browse BlockStateProperties")
    p_props.add_argument("query", nargs="?", default="", help="Optional property filter")

    # packets
    p_pkts = subparsers.add_parser("packets", help="Browse network protocol packets")
    p_pkts.add_argument("query", nargs="?", default="", help="Optional packet filter")

    # usages
    p_usages = subparsers.add_parser("usages", aliases=["references", "refs"], help="Search symbol references in sources")
    p_usages.add_argument("symbol", help="Method, field, or class name to search")
    p_usages.add_argument("--limit", type=int, default=30)

    # classpath
    subparsers.add_parser("classpath", help="Print resolved classpath")

    return parser


def main():
    repo_root = get_repo_root()

    # Pre-parse platform arguments if present
    platform_pref = None
    custom_sources = None
    custom_merged = None

    for i, arg in enumerate(sys.argv):
        if arg == "--platform" and i + 1 < len(sys.argv):
            platform_pref = sys.argv[i + 1]
        elif arg == "--sources" and i + 1 < len(sys.argv):
            custom_sources = sys.argv[i + 1]
        elif arg == "--jar" and i + 1 < len(sys.argv):
            custom_merged = sys.argv[i + 1]

    index = JarIndex(
        repo_root,
        platform_pref=platform_pref,
        custom_sources=custom_sources,
        custom_merged=custom_merged
    )

    if len(sys.argv) > 1 and sys.argv[1] in ("shell", "repl", "-i"):
        run_repl(index)
        return

    dispatch_command(sys.argv[1:], index)


if __name__ == "__main__":
    main()
