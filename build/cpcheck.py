"""cpcheck.py: structural validation of class files (constant pool + members + attributes).

Catches the defect that broke MinecraftJ2ME(3).jar: method/field/class attributes whose name
index points past the constant pool (e.g. "Code" missing from the pool).

usage: python -I cpcheck.py <dir-or-jar-or-class>
exit code 1 if any class is malformed.
"""
import os, struct, sys, zipfile

CP_SIZE = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}


def check(data):
    """Return a list of problems (empty when the class is structurally valid)."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return ["bad magic"]
    try:
        n = struct.unpack(">H", data[8:10])[0]
        p = 10
        tag = {}
        utf = {}
        refs = []  # (cp index, list of referenced cp indices, kind)
        i = 1
        while i < n:
            t = data[p]
            if t == 1:
                ln = struct.unpack(">H", data[p + 1:p + 3])[0]
                utf[i] = data[p + 3:p + 3 + ln]
                tag[i] = 1
                p += 3 + ln
            elif t in CP_SIZE:
                body = data[p + 1:p + 1 + CP_SIZE[t]]
                tag[i] = t
                if t in (7, 8, 16, 19, 20):
                    refs.append((i, [struct.unpack(">H", body)[0]], "utf/class"))
                elif t in (9, 10, 11, 12, 17, 18):
                    refs.append((i, list(struct.unpack(">HH", body)), "nat/ref"))
                p += 1 + CP_SIZE[t]
            else:
                return ["unknown cp tag %d at #%d" % (t, i)]
            i += 2 if t in (5, 6) else 1

        errs = []
        for idx, targets, kind in refs:
            for v in targets:
                if v not in tag and v != 0:
                    errs.append("cp #%d (%s) -> missing #%d" % (idx, kind, v))
        if errs:
            return errs[:3]

        def utf_ok(ix):
            return tag.get(ix) == 1

        def attrs(p):
            cnt = struct.unpack(">H", data[p:p + 2])[0]
            p += 2
            for _ in range(cnt):
                if not utf_ok(struct.unpack(">H", data[p:p + 2])[0]):
                    raise ValueError("attribute name not a Utf8 entry")
                ln = struct.unpack(">I", data[p + 2:p + 6])[0]
                p += 6 + ln
            return p

        this_ix = struct.unpack(">H", data[p + 2:p + 4])[0]
        super_ix = struct.unpack(">H", data[p + 4:p + 6])[0]
        if tag.get(this_ix) != 7 or (super_ix and tag.get(super_ix) != 7):
            return ["this/super_class is not a Class entry"]
        p += 6  # access, this, super
        ifc = struct.unpack(">H", data[p:p + 2])[0]
        p += 2 + 2 * ifc
        for _ in range(2):  # fields, then methods
            cnt = struct.unpack(">H", data[p:p + 2])[0]
            p += 2
            for _ in range(cnt):
                if not utf_ok(struct.unpack(">H", data[p + 2:p + 4])[0]) or not utf_ok(struct.unpack(">H", data[p + 4:p + 6])[0]):
                    return ["member name/descriptor not a Utf8 entry"]
                p = attrs(p + 6)
        p = attrs(p)  # class attributes
        if p != len(data):
            return ["%d trailing bytes" % (len(data) - p)]
        return []
    except (struct.error, IndexError, ValueError) as e:
        return ["truncated or malformed: %s" % e]


def files(target):
    if os.path.isdir(target):
        for root, _, fs in os.walk(target):
            for f in fs:
                if f.endswith(".class"):
                    p = os.path.join(root, f)
                    yield p, open(p, "rb").read()
    elif target.endswith(".class"):
        yield target, open(target, "rb").read()
    else:
        with zipfile.ZipFile(target) as z:
            for n in z.namelist():
                if n.endswith(".class"):
                    yield n, z.read(n)


if __name__ == "__main__":
    total = bad = 0
    for name, data in files(sys.argv[1]):
        total += 1
        e = check(data)
        if e:
            bad += 1
            if bad <= 15:
                print(name, e)
    print("classes=%d malformed=%d" % (total, bad))
    sys.exit(1 if bad else 0)
