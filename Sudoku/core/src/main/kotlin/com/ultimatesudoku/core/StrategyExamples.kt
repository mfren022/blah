package com.ultimatesudoku.core

/**
 * Positions to practise each technique on, three apiece, written as in [Example.parse]. They come from
 * random puzzles. Beyond the singles, each is taken where no single is left on the board and no easier
 * technique could make the same deduction. The pencil marks are just what the placed digits leave, so
 * every candidate shown is plain to check.
 */
internal val EXAMPLES: Map<Technique, List<String>> = mapOf(
    Technique.FULL_HOUSE to listOf(
        "...728.9e...cf5...2e7a4ih36...bc19..9..5h7..3..24if...63.h1.5.4...6......1.954...",
        "4..ib1.5.5..87f.a....3de.2...3.hie.77d95631h28...a.9...6.ae4.......98..1.1.6....5",
        ".4532.76.9gf..12.....67..5.7.98.b......7.6........35.7.1gi84.....41fg..8.98b3547a",
    ),
    Technique.HIDDEN_SINGLE to listOf(
        "...18....183db..5...2.5.h4a.3.6ge1..5fab....7.b7.a4e6..7..6a2..b1....695....42.a.",
        "..9.4..6...6i.28..8.e3f19...736...81.h..1....65a..349..f71.5..9ei82..7a.a3..9.5..",
        "8.61.....a4g2..9h.e.3.8.a.7.5h7.a62.6g9.b.4a8.12..8.7.9...1.7.b..1..2.9......48.1",
    ),
    Technique.NAKED_SINGLE to listOf(
        "......c.a8a...3f94.431.f.2h7..4..1.i..16.52dg..4.a7h.5a2...948c53.8....6d.......b",
        "..7...5dh.8...4.26..2....39...14628..2fg8i.1..18235if.29....8e.86.9e..7...5...6i.",
        ".3..b7d..2..4......5..8c2719...45..6.6..g..9.5..39f..4398.6..4ggae.c4..2fdb7...3.",
    ),
    Technique.POINTING to listOf(
        ".fg..2.ch3....72.684be631gigcfbdih154.9.g.6b328.c.fgid..872dc616b39....7agd6c.i.b",
        "..948.3.b.4..395h.ch.f..1id9c6gad2eh7bd5i8fa3ha5cfb7d9.i3h..d..d.197ch2..g8.469c.",
        "a.i8f3...536.b.h1..h2.1ec.6f185id2cg9..3h2fd1cb4fg195h8.c.4.1...9a.ef483...1c8...",
    ),
    Technique.CLAIMING to listOf(
        "b5f1cihdg9d8bfg3..7ac45hf2i8i2g.6....6e9h3.7bcg.5..9f8e3if71bh4..7.i.5.6f....5g9.",
        "63a2hgid5.9.5ac.f7..7dfic1...c9g8.2abg8a4f5cii1.3b5.g.g8.f.b1e.1..h.4g9.3..g.1.86",
        "f8e...4g9...id7.6.d9gf8eabc92ae76chd7e6d3h2i1cdh19bf57....6d.1..6.8..gd.8.4....3f",
    ),
    Technique.NAKED_PAIR to listOf(
        "d6g.3.ia.c196.dgb.82e1i7df3.h4....39.5f3.9h4.29cd..5g.6db9a5c87ich..615degah4c.9.",
        "3952f.4.aa.24cei.f.4.1..53b4..6.abe8.b1ce.7fd5...d2ca9g14eb6h9c.....41bebe8ia3647",
        "9..ge42.858.3bia.g7.216hie.db5hgcf193...aeg.217.b.f5..f...328g5he.f.7.212.75ha.i6",
    ),
    Technique.HIDDEN_PAIR to listOf(
        "87.cbai6....9gf8b.2if45h.g.95hacgb461..6i2.e862.hde.19chbe14fi7g.9bf3.h..6.ghi.32",
        "f8e...4g9...id7.6.d9gf8eabc92ae76chd7e6d3h2i1cdh19bf57....6d.1..6.8..gd.8.4....3f",
        "9g.1..f5..2.76..9..fa.5i7.815.h.f2.i.9...eh1f.h6i.a.756d9e8g...g1bf93e8d.3.da2if7",
    ),
    Technique.NAKED_TRIPLE to listOf(
        "87.cbai6....9gf8b.2if45h.g.95hacgb461..6i2.e862.hde.19chbe14fi7g.9bf3.h..6.ghi.32",
        "hc6...dbii5a2df83g74bh9c5fa...f78c.2.2g4c9a8.3.h52a..d.g3i6.b18.89..2.4.b...h.9..",
        "a34g..i6h..ic.1.eb.2e.i83a.2ig...f83ca82g65di54fhcibg1.e19.c.2.i.b5.gac..7c...89e",
    ),
    Technique.HIDDEN_TRIPLE to listOf(
        "...92.gha.2i.h7cd6.87.34eib978...2cd..6cdb9ghbc4hgi165..b75.81i8i.2..d5gg..d98fbc",
        "aec4hi72fgfd2c5h9a.i.g1643e..7..16..6a......4..96..1..c2196hedgi7f3e4bahd85ag2.f.",
        "d86.5..c.bcgh96ae4iaedb387f79.e...4.e6.b...1.c2.....85.739.edba1di37befh.eb.4.39g",
    ),
    Technique.X_WING to listOf(
        "b.6.g5id8.98.6.57.e.798.36.7.i.a6bhe.2ag5.f9d.fe2i.ac7f82e374aia53h4i72f9gd6ba8ec",
        "4f5i71..b.2.3de...3g.2f8.e4.cf15g42.1.bdcf..5g54h92...6..7b3ed1bdgea9.6.eac68d2i7",
        "g.49.h.2115bf74ihc8i..2..dge.a.6b7id6d...iba8b.9.4.cfeib.d8ca.6d..21.h3931h.i64.b",
    ),
    Technique.SKYSCRAPER to listOf(
        ".c.7hbe41hdac65g9bb574iachf.gei1h.23c92fed17h18.b3g.e.ebcad986g.1h57fbc.76.hb3.ae",
        "c8..41.962a.fi.8.4fdi.h5.ag76a.b84.idib.1..fhhe34fia72ich1gbfde5b6icdgh117d85fi2c",
        ".8e6a7.43fcad2ihe7...5h3f.ac96bea7hd1gh9c4ef2de2hgf31i.f.3d8a.e8.ca9e.gf51.7f2.3h",
    ),
    Technique.TWO_STRING_KITE to listOf(
        "..ce7f4bhd8gai236565bhdci7a8..gb4.cf376iea842bd.6chg.9g2.cf..845684agb9c.c4b8.f.g",
        ".h..1.7i3aig8bce463df579bh1h713..i.df.i748ac..cd..186g9f.137d.841h..5cg.7.3.8.fa.",
        "iah.b.e6.g26.e..i43d5i6.12.6eg..2.4ibhc749feaa9d5.fg.2d61b7.9.58gb.ie41.e3i...bg.",
    ),
    Technique.NAKED_QUAD to listOf(
        "8..6e7cad5cfida2hga7db839f5c.81bd.7..a7.6e4...4..g91..4.173f.2...3eib.d1...4a8..3",
        "2h.3....717..h..32e43...h.ig3bh149efdf15i32ghie827fa4cca....68e82.....946i...8.b1",
        "6..had572abd7ce..i5..2fi3a4...68a.9e...5b7.c..1ed93..h3.1i.2..7...c.8...842agfie3",
    ),
    Technique.HIDDEN_QUAD to listOf(
        "da582fi3gig3ed1f8b86big3a45.8...4..9.i..1....5c.2i..1.75i4.b.61.2.1..7id.4.g692e.",
        "...4.268.hb.3.i7...5.a.8..26.h9b53..5..8.3b.6.c27.6..83ae6.g.2.df7b.1...b895c4afg",
        ".4.25c7fhe78.af...3f28...1.2.6....8a...1f8....8..cb6.9h5cf.41.2.a..b.83..b7c81.5.",
    ),
    Technique.XY_WING to listOf(
        "1dgi6bec8b35hga6di98fc45abg8e.7ac.f.c62eid78a..abh6.e3e..13h.76..8fei31.6acd2ghi5",
        "67842ae9cc9agf5d82..dch9g1f4.f2igh3.g.c.ehi.d.8i.d3..7i6g8cd...13e9gbf4hh4be16379",
        "9......f5ah5f94c276...e.9.8g9b1c6ehd3ah7d5fi2def9h2g3a2c4ef...987i41c2ef5fa..i..3",
    ),
    Technique.XYZ_WING to listOf(
        "..dhig6c.78cebfd19.9.341h.g.4.9e2378ibgfcheda8357a4b9fc..469g8.47ibhea63.f8agci.d",
        "67842ae9cc9agf5d82..dch9g1f4.f2igh3.g.c.ehi.d.8i.d3..7i6g8cd...13e9gbf4hh4be16379",
        "fic....g4d21.7i.h65g8....2i2d5g61h9cacf9h5gdbg8i23d6e1i1b.e.4c73eda9g26h8fg...iae",
    ),
    Technique.W_WING to listOf(
        ".c.7hbe41hdac65g9bb574iachf.gei1h.23c92fed17h18.b3g.e.ebcad986g.1h57fbc.76.hb3.ae",
        "67842ae9cc9agf5d82..dch9g1f4.f2igh3.g.c.ehi.d.8i.d3..7i6g8cd...13e9gbf4hh4be16379",
        "76ae..3hicb4ha97fehi536ga4b4g..9...c9.c7.8..6b...3..i7e3ib816gdfh24gc9eaad7iefb38",
    ),
    Technique.SWORDFISH to listOf(
        "1.g.f3.4936..497b1i.41...63d3..a8.gf6a.d...c8g..9cfd1e54cgh16ib2g169ec8489f3bdae7",
        "hf5.7.c.ig1b39e..hi4c.f8g.5a84ibc..gb39f5g18degfhda93b6.a7h..9cc.h.19.7fdig.3f8.a",
        "7da6.eh.b6e2d8..ga83972aedf.g6hd.215.8.bagd6.124..f7h.di..68127.ahi7b6.4bfga.4..8",
    ),
    Technique.SIMPLE_COLORING to listOf(
        ".1e.h..63..35a.8i.8.g.c65a2.5h1b..c.agb364iehc.d.e8a2.4c12gefh9gb6hi13de58ifdcb7a",
        "ifa.b.cd857cd1hbi6bdh93fa7e4cf1.i.2g8a2...4.9g5i.d2f.1a8eb74ifc6bg.9..143id..ag.b",
        "..h4..g65..e7..d824.7.8.c1i.h9.dga23..1c..8dg37dabh9efh4ci5f2g119fbg4ech75bhc1fid",
    ),
    Technique.UNIQUE_RECTANGLE to listOf(
        "b.c7564.a4a.c2.6g5gef..a..3efa9hd7c2hc2f1g5di9g4ec2haf1....ecfg3.7.6.ae4fd5173..h",
        "4eg..23a8a6cg8dbi5hbiae376d.d.c..9h7.9.h4g.3.7c8....d.i842gfaec6abe3hd7i3g54..hb6",
        "8i1ceg62dc4e26ah9gfb7h4ie..g8..b5...9...g..e8e..9h.g6.dgie1b3hfa5hg36.4.b63dih1g5",
    ),
    Technique.BUG_PLUS_ONE to listOf(
        ".d1.c68ieic84ea672..f89.cda4h2afcg596a9.b.4c837eihd2a6a.c.78ifd864ca95bg.i76d.1hc",
        "ec24..fa8fh95b1c47gdaf8ceb91.ci.d862ib.hf.dca468cabgi58.da3.bgf21f7d89ec3..b.61hd",
        "2.4.f8i.eh195bcf4g.ef4.9b.85b3ihagfd6d7ceb1h9aihfdg3e29.e8.6dba.8b..459fdfa2ie8g3",
    ),
    Technique.JELLYFISH to listOf(
        "dbe3i1786..6eghbdcgch26419e...f2e.3737...ie6265b.3gia..63952.g.b.g..f3ei5497.3fb.",
        ".g.h361e4h1e...7cf6dc7eah2ieh4f.g.137.f3a4.h513..he6dg.5a.g8cf2.f7a.ce9h3.856.dga",
        "bea46hic738f..7b5dg4ic2eha6..7h.be9c.ih5.3.bae2c...4.h9.db3..8e.3e7hi.428.b.54c.i",
    ),
    Technique.XY_CHAIN to listOf(
        "fic....g4d21.7i.h65g8....2i2d5g61h9cacf9h5gdbg8i23d6e1i1b.e.4c73eda9g26h8fg...iae",
        "1dgi6bec8b35hga6di98fc45abg8e.7ac.f.c62eid78a..abh6.e3e..13h.76..8fei31.6acd2ghi5",
        ".8e6a7.43fcad2ihe7...5h3f.ac96bea7hd1gh9c4ef2de2hgf31i.f.3d8a.e8.ca9e.gf51.7f2.3h",
    ),
)
